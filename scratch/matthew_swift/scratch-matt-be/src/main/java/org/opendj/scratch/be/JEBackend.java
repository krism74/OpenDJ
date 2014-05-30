package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.opendj.scratch.be.Util.clearAndCreateDbDir;
import static org.opendj.scratch.be.Util.decodeEntry;
import static org.opendj.scratch.be.Util.encodeDescription;
import static org.opendj.scratch.be.Util.internalError;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public final class JEBackend implements Backend {
    private static final File DB_DIR = new File("jeBackend");

    private Environment env = null;
    private Database description2id = null;
    private Database dn2id = null;
    private Database id2entry = null;

    @Override
    public void close() {
        if (description2id != null) {
            description2id.close();
            description2id = null;
        }
        if (dn2id != null) {
            dn2id.close();
            dn2id = null;
        }
        if (id2entry != null) {
            id2entry.close();
            id2entry = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    @Override
    public void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        clearAndCreateDbDir(DB_DIR);
        initialize(options, true);
        try {
            for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                final Entry entry = entries.readEntry();
                final DatabaseEntry dbId = encodeEntryId(nextEntryId);
                dn2id.put(null, encodeDn(entry.getName()), dbId);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    final DatabaseEntry key = new DatabaseEntry(encodedDescription.toByteArray());
                    description2id.put(null, key, dbId);
                }
                id2entry.put(null, dbId, encodeEntry(entry));
            }
        } finally {
            close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        initialize(options, false);
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws ErrorResultException {
        final TransactionConfig config = new TransactionConfig();
        final Transaction txn = env.beginTransaction(null, config);
        try {
            // Read entry and apply updates.
            final DatabaseEntry dbId = readDn2Id(txn, request.getName());
            final Entry entry = readId2Entry(txn, dbId, true);
            final ByteString oldDescriptionKey = encodeDescription(entry);
            Entries.modifyEntry(entry, request);
            final ByteString newDescriptionKey = encodeDescription(entry);
            // Update description index.
            final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
            if (comparison != 0) {
                final DatabaseEntry oldKey = new DatabaseEntry(oldDescriptionKey.toByteArray());
                final DatabaseEntry newKey = new DatabaseEntry(newDescriptionKey.toByteArray());
                if (comparison < 0) {
                    description2id.delete(txn, oldKey);
                    description2id.put(txn, newKey, dbId);
                } else {
                    description2id.put(txn, newKey, dbId);
                    description2id.delete(txn, oldKey);
                }
            }
            // Update id2entry index.
            id2entry.put(txn, dbId, encodeEntry(entry));
            txn.commit();
        } catch (final Exception e) {
            throw internalError(e);
        } finally {
            txn.abort();
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws ErrorResultException {
        try {
            final DatabaseEntry dbKey =
                    new DatabaseEntry(encodeDescription(description).toByteArray());
            final DatabaseEntry dbId = new DatabaseEntry();
            if (description2id.get(null, dbKey, dbId, LockMode.READ_COMMITTED) != OperationStatus.SUCCESS) {
                throw newErrorResult(ResultCode.NO_SUCH_OBJECT);
            }
            return readId2Entry(null, dbId, false);
        } catch (final Exception e) {
            throw internalError(e);
        }
    }

    @Override
    public Entry readEntryByDN(final DN name) throws ErrorResultException {
        try {
            return readId2Entry(null, readDn2Id(null, name), false);
        } catch (final Exception e) {
            throw internalError(e);
        }
    }

    private DatabaseEntry encodeDn(final DN dn) throws ErrorResultException {
        return new DatabaseEntry(Util.encodeDn(dn).toByteArray());
    }

    private DatabaseEntry encodeEntry(final Entry entry) throws IOException {
        return new DatabaseEntry(Util.encodeEntry(entry));
    }

    private DatabaseEntry encodeEntryId(final long entryId) {
        return new DatabaseEntry(ByteString.valueOf(entryId).toByteArray());
    }

    private void initialize(final Map<String, String> options, final boolean isImport)
            throws Exception {
        final EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(!isImport);
        envConfig.setAllowCreate(true);
        envConfig.setLockTimeout(0, TimeUnit.MICROSECONDS);
        envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
        envConfig.setCachePercent(60);
        env = new Environment(DB_DIR, envConfig);

        final DatabaseConfig dbConfig =
                new DatabaseConfig().setAllowCreate(true).setKeyPrefixing(true).setTransactional(
                        !isImport).setDeferredWrite(isImport);
        id2entry = env.openDatabase(null, "id2entry", dbConfig);
        dn2id = env.openDatabase(null, "dn2id", dbConfig);
        description2id = env.openDatabase(null, "description2id", dbConfig);
    }

    private DatabaseEntry readDn2Id(final Transaction txn, final DN name)
            throws ErrorResultException {
        final DatabaseEntry dbKey = encodeDn(name);
        final DatabaseEntry dbId = new DatabaseEntry();
        if (dn2id.get(txn, dbKey, dbId, LockMode.READ_COMMITTED) != OperationStatus.SUCCESS) {
            throw newErrorResult(ResultCode.NO_SUCH_OBJECT);
        }
        return dbId;
    }

    private Entry readId2Entry(final Transaction txn, final DatabaseEntry dbId, final boolean isRMW)
            throws ErrorResultException {
        final LockMode lockMode = isRMW ? LockMode.RMW : LockMode.READ_COMMITTED;
        final DatabaseEntry dbEntry = new DatabaseEntry();
        if (id2entry.get(txn, dbId, dbEntry, lockMode) != OperationStatus.SUCCESS) {
            throw newErrorResult(ResultCode.NO_SUCH_OBJECT);
        }
        return decodeEntry(dbEntry.getData());
    }

}
