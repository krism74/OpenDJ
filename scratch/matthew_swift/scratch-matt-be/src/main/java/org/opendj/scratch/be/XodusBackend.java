package org.opendj.scratch.be;

import static jetbrains.exodus.env.StoreConfig.USE_EXISTING;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.opendj.scratch.be.Util.adaptException;
import static org.opendj.scratch.be.Util.clearAndCreateDbDir;
import static org.opendj.scratch.be.Util.decodeEntry;
import static org.opendj.scratch.be.Util.encodeDescription;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.env.TransactionalComputable;
import jetbrains.exodus.env.TransactionalExecutable;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

public final class XodusBackend implements Backend {
    private static final File DB_DIR = new File("target/xodusBackend");

    private Store description2id = null;
    private Store dn2id = null;
    private Environment env = null;
    private Store id2entry = null;

    @Override
    public void close() {
        if (env != null) {
            env.close();
            env = null;
            id2entry = null;
            dn2id = null;
            description2id = null;
        }
    }

    @Override
    public void initialize(Map<String, String> options) throws Exception {
        // No op
    }

    @Override
    public void importEntries(final EntryReader entries) throws Exception {
        clearAndCreateDbDir(DB_DIR);
        open(true);
        final Transaction txn = env.beginTransaction();
        try {
            for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                final Entry entry = entries.readEntry();
                final ByteIterable dbId = encodeEntryId(nextEntryId);
                dn2id.put(txn, encodeDn(entry.getName()), dbId);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    final ByteIterable key =
                            new ArrayByteIterable(encodedDescription.toByteArray());
                    description2id.put(txn, key, dbId);
                }
                id2entry.put(txn, dbId, encodeEntry(entry));
            }
        } finally {
            txn.commit();
            close();
        }
    }

    @Override
    public void open() throws Exception {
        open(false);
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        final AtomicReference<LdapException> error = new AtomicReference<LdapException>();
        env.executeInTransaction(new TransactionalExecutable() {
            @Override
            public void execute(final Transaction txn) {
                // Read entry and apply updates.
                try {
                    final ByteIterable dbId = readDn2Id(txn, request.getName());
                    final Entry entry = readId2Entry(txn, dbId, true);
                    final ByteString oldDescriptionKey = encodeDescription(entry);
                    Entries.modifyEntry(entry, request);
                    final ByteString newDescriptionKey = encodeDescription(entry);
                    // Update description index.
                    final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
                    if (comparison != 0) {
                        final ByteIterable oldKey =
                                new ArrayByteIterable(oldDescriptionKey.toByteArray());
                        final ByteIterable newKey =
                                new ArrayByteIterable(newDescriptionKey.toByteArray());
                        description2id.delete(txn, oldKey);
                        description2id.put(txn, newKey, dbId);
                    }
                    // Update id2entry index.
                    id2entry.put(txn, dbId, encodeEntry(entry));
                } catch (final Exception e) {
                    error.set(adaptException(e));
                }
            }
        });
        final LdapException e = error.get();
        if (e != null) {
            throw e;
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws LdapException {
        final AtomicReference<LdapException> error = new AtomicReference<LdapException>();
        final Entry entry = env.computeInReadonlyTransaction(new TransactionalComputable<Entry>() {
            @Override
            public Entry compute(final Transaction txn) {
                try {
                    final ByteIterable dbKey =
                            new ArrayByteIterable(encodeDescription(description).toByteArray());
                    final ByteIterable dbId = description2id.get(txn, dbKey);
                    if (dbId == null) {
                        throw newLdapException(ResultCode.NO_SUCH_OBJECT);
                    }
                    return readId2Entry(txn, dbId, false);
                } catch (final Exception e) {
                    error.set(adaptException(e));
                    return null;
                }
            }
        });
        if (entry != null) {
            return entry;
        }
        throw error.get();
    }

    @Override
    public Entry readEntryByDN(final DN name) throws LdapException {
        final AtomicReference<LdapException> error = new AtomicReference<LdapException>();
        final Entry entry = env.computeInReadonlyTransaction(new TransactionalComputable<Entry>() {
            @Override
            public Entry compute(final Transaction txn) {
                try {
                    return readId2Entry(txn, readDn2Id(txn, name), false);
                } catch (final Exception e) {
                    error.set(adaptException(e));
                    return null;
                }
            }
        });
        if (entry != null) {
            return entry;
        }
        throw error.get();
    }

    private ByteIterable encodeDn(final DN dn) {
        return new ArrayByteIterable(Util.encodeDn(dn).toByteArray());
    }

    private ByteIterable encodeEntry(final Entry entry) throws IOException {
        return new ArrayByteIterable(Util.encodeEntry(entry));
    }

    private ByteIterable encodeEntryId(final long entryId) {
        return new ArrayByteIterable(ByteString.valueOf(entryId).toByteArray());
    }

    private void open(final boolean isImport) throws Exception {
        final EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setLogFileSize(100 * 1024);
        envConfig.setLogCachePageSize(2 * 1024 * 1024);
        env = Environments.newInstance(DB_DIR, envConfig);
        final StoreConfig storeConfig = isImport ? WITHOUT_DUPLICATES_WITH_PREFIXING : USE_EXISTING;
        env.executeInTransaction(new TransactionalExecutable() {
            @Override
            public void execute(final Transaction txn) {
                id2entry = env.openStore("id2entry", storeConfig, txn);
                dn2id = env.openStore("dn2id", storeConfig, txn);
                description2id = env.openStore("description2id", storeConfig, txn);
            }
        });
    }

    private ByteIterable readDn2Id(final Transaction txn, final DN name) throws LdapException {
        final ByteIterable dbKey = encodeDn(name);
        final ByteIterable dbId = dn2id.get(txn, dbKey);
        if (dbId == null) {
            throw newLdapException(ResultCode.NO_SUCH_OBJECT);
        }
        return dbId;
    }

    private Entry readId2Entry(final Transaction txn, final ByteIterable dbId, final boolean isRMW)
            throws LdapException {
        final ByteIterable dbEntry = id2entry.get(txn, dbId);
        if (dbEntry == null) {
            throw newLdapException(ResultCode.NO_SUCH_OBJECT);
        }
        return decodeEntry(dbEntry.getBytesUnsafe(), dbEntry.getLength());
    }

}
