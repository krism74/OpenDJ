package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.opendj.scratch.be.Util.adaptException;
import static org.opendj.scratch.be.Util.clearAndCreateDbDir;
import static org.opendj.scratch.be.Util.decodeEntry;
import static org.opendj.scratch.be.Util.encodeEntry;

import java.io.File;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.TreeBuilder;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

public final class PersistItBackend implements Backend {
    private static final File DB_DIR = new File("target/persistItBackend");

    private Properties properties;
    private Persistit db;
    private Volume volume;

    @Override
    public void initialize(Map<String, String> options) throws Exception {
        properties = new Properties();
        properties.setProperty("datapath", DB_DIR.toString());
        properties.setProperty("logpath", DB_DIR.toString());
        properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
        properties.setProperty("buffer.count.16384", "64K");
        properties.setProperty("volume.1", "${datapath}/dj,create,pageSize:16K,"
                + "initialSize:50M,extensionSize:1M,maximumSize:10G");
        properties.setProperty("journalpath", "${datapath}/dj_journal");
    }

    @Override
    public void importEntries(EntryReader entries) throws Exception {
        clearAndCreateDbDir(DB_DIR);
        open();
        final Tree id2entry = volume.getTree("id2entry", true);
        final Tree dn2id = volume.getTree("dn2id", true);
        final Tree description2id = volume.getTree("description2id", true);
        final TreeBuilder tb = new TreeBuilder(db);
        try {
            final Key key = new Key(db);
            final Value value = new Value(db);
            for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                final Entry entry = entries.readEntry();

                // id2entry
                final byte[] dbId = ByteString.valueOf(nextEntryId).toByteArray();
                value.putByteArray(encodeEntry(entry));
                tb.store(id2entry, keyOf(key, dbId), value);

                // dn2id
                value.putByteArray(dbId);
                tb.store(dn2id, encodeDn(key, entry.getName()), value);

                // description2id
                final ByteString encodedDescription = Util.encodeDescription(entry);
                if (encodedDescription != null) {
                    tb.store(description2id, encodeDescription(key, encodedDescription), value);
                }
            }
            tb.merge();
        } finally {
            close();
        }
    }

    @Override
    public void open() throws Exception {
        db = new Persistit(properties);
        db.initialize();
        volume = db.loadVolume("dj");
    }

    @Override
    public void close() {
        if (db != null) {
            try {
                db.close();
                db = null;
            } catch (PersistitException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Key encodeDescription(final Key key, final ByteString encodedDescription) {
        return keyOf(key, encodedDescription.toByteArray());
    }

    private Key encodeDn(final Key key, final DN dn) {
        return keyOf(key, dn.toIrreversibleNormalizedByteString().toByteArray());
    }

    private Key keyOf(final Key key, final byte[] bytes) {
        return key.clear().appendByteArray(bytes, 0, bytes.length);
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        final Transaction txn = db.getTransaction();
        try {
            for (;;) {
                txn.begin();
                try {
                    final byte[] dbId = readDn2Id(txn, request.getName());
                    final Entry entry = readId2Entry(txn, dbId, true);
                    final ByteString oldDescriptionKey = Util.encodeDescription(entry);
                    Entries.modifyEntry(entry, request);
                    final ByteString newDescriptionKey = Util.encodeDescription(entry);
                    // Update description index.
                    final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
                    if (comparison != 0) {
                        final Exchange ex = db.getExchange(volume, "description2id", false);
                        encodeDescription(ex.getKey(), oldDescriptionKey);
                        ex.remove();
                        encodeDescription(ex.getKey(), newDescriptionKey);
                        ex.getValue().putByteArray(dbId);
                        ex.store();
                        db.releaseExchange(ex);
                    }
                    // Update id2entry index.
                    final Exchange ex = db.getExchange(volume, "id2entry", false);
                    keyOf(ex.getKey(), dbId);
                    ex.getValue().putByteArray(encodeEntry(entry));
                    ex.store();
                    db.releaseExchange(ex);
                    txn.commit();
                    break;
                } catch (RollbackException e) {
                    // Retry.
                } catch (Exception e) {
                    txn.rollback();
                    throw e;
                } finally {
                    txn.end();
                }
            }
        } catch (Exception e) {
            throw adaptException(e);
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws LdapException {
        return readEntry(new IndexedRead() {
            @Override
            public byte[] readIndex(Transaction txn) throws Exception {
                return readDescription2Id(txn, description);
            }
        });
    }

    private interface IndexedRead {
        byte[] readIndex(Transaction txn) throws Exception;
    }

    @Override
    public Entry readEntryByDN(final DN name) throws LdapException {
        return readEntry(new IndexedRead() {
            @Override
            public byte[] readIndex(Transaction txn) throws Exception {
                return readDn2Id(txn, name);
            }
        });
    }

    private Entry readEntry(IndexedRead reader) throws LdapException {
        final Transaction txn = db.getTransaction();
        try {
            txn.begin();
            try {
                final Entry entry = readId2Entry(txn, reader.readIndex(txn), false);
                txn.commit();
                return entry;
            } finally {
                txn.end();
            }
        } catch (Exception e) {
            throw adaptException(e);
        }
    }

    private byte[] readDn2Id(final Transaction txn, final DN name) throws Exception {
        final Exchange ex = db.getExchange(volume, "dn2id", false);
        try {
            encodeDn(ex.getKey(), name);
            ex.fetch();
            final Value dbId = ex.getValue();
            if (!dbId.isDefined()) {
                throw newLdapException(ResultCode.NO_SUCH_OBJECT);
            }
            return dbId.getByteArray();
        } finally {
            db.releaseExchange(ex);
        }
    }

    private byte[] readDescription2Id(Transaction txn, ByteString description) throws Exception {
        final Exchange ex = db.getExchange(volume, "description2id", false);
        try {
            keyOf(ex.getKey(), description.toByteArray());
            ex.fetch();
            final Value dbId = ex.getValue();
            if (!dbId.isDefined()) {
                throw newLdapException(ResultCode.NO_SUCH_OBJECT);
            }
            return dbId.getByteArray();
        } finally {
            db.releaseExchange(ex);
        }
    }

    private Entry readId2Entry(final Transaction txn, final byte[] dbId, final boolean isRMW)
            throws Exception {
        final Exchange ex = db.getExchange(volume, "id2entry", false);
        try {
            keyOf(ex.getKey(), dbId);
            ex.fetch();
            final Value dbEntry = ex.getValue();
            if (!dbEntry.isDefined()) {
                throw newLdapException(ResultCode.NO_SUCH_OBJECT);
            }
            return decodeEntry(dbEntry.getByteArray());
        } finally {
            db.releaseExchange(ex);
        }
    }

}
