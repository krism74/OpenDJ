package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.*;

import java.io.File;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

import com.orientechnologies.common.serialization.types.OBinaryTypeSerializer;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.ORuntimeKeyIndexDefinition;
import com.orientechnologies.orient.core.intent.OIntentMassiveInsert;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.tx.OTransaction.TXTYPE;

public final class OrientBackend implements Backend {
    private static final class DbHolder {
        private final ODatabaseDocumentTx db;
        private final OIndex<?> description2entry;
        private final OIndex<?> dn2entry;

        public DbHolder(final ODatabaseDocumentTx db) {
            this.db = db;
            this.dn2entry = db.getMetadata().getIndexManager().getIndex("dn2entry");
            this.description2entry =
                    db.getMetadata().getIndexManager().getIndex("description2entry");
        }

    }

    private static final File DB_DIR = new File("target/orientBackend");
    private static final String DB_URL = "plocal:" + DB_DIR.getAbsolutePath();

    private final Queue<ODatabaseDocumentTx> activeDbConnections =
            new ConcurrentLinkedQueue<ODatabaseDocumentTx>();

    private final ThreadLocal<DbHolder> threadLocalDb = new ThreadLocal<DbHolder>() {
        @Override
        protected DbHolder initialValue() {
            final ODatabaseDocumentTx db = new ODatabaseDocumentTx(DB_URL).open("admin", "admin");
            activeDbConnections.add(db);
            return new DbHolder(db);
        }
    };

    @Override
    public void close() {
        for (final ODatabaseDocumentTx db : activeDbConnections) {
            db.close();
        }
    }

    @Override
    public void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        clearAndCreateDbDir(DB_DIR);
        //        OGlobalConfiguration.USE_WAL.setValue(false);
        //        OGlobalConfiguration.TX_USE_LOG.setValue(false);
        final ODatabaseDocumentTx db = new ODatabaseDocumentTx(DB_URL).create();
        final OIndex<?> dn2entry =
                db.getMetadata().getIndexManager().createIndex("dn2entry", "UNIQUE",
                        new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID), null,
                        null, null);
        final OIndex<?> description2entry =
                db.getMetadata().getIndexManager().createIndex("description2entry", "UNIQUE",
                        new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID), null,
                        null, null);
        db.declareIntent(new OIntentMassiveInsert());
        try {
            while (entries.hasNext()) {
                final Entry entry = entries.readEntry();
                final ORecordBytes entryRecord = new ORecordBytes(db, encodeEntry(entry));
                entryRecord.save();
                dn2entry.put(encodeDn(entry.getName()).toByteArray(), entryRecord);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    description2entry.put(encodedDescription.toByteArray(), entryRecord);
                }
            }
        } finally {
            db.close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        // OGlobalConfiguration.CACHE_LEVEL2_ENABLED.setValue(true);
        // OGlobalConfiguration.CACHE_LEVEL2_SIZE.setValue(100000);
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        final DbHolder dbHolder = threadLocalDb.get();
        final byte[] dnKey = encodeDn(request.getName()).toByteArray();
        while (true) {
            dbHolder.db.begin(TXTYPE.OPTIMISTIC);
            try {
                final ORecordId id = (ORecordId) dbHolder.dn2entry.get(dnKey);
                final ORecordBytes entryRecord = dbHolder.db.getRecord(id);
                final Entry entry = decodeEntry(entryRecord.toStream());
                final ByteString oldDescriptionKey = encodeDescription(entry);
                Entries.modifyEntry(entry, request);
                final ByteString newDescriptionKey = encodeDescription(entry);
                entryRecord.setDirty();
                entryRecord.fromStream(encodeEntry(entry));
                entryRecord.save();
                // Update description index.
                final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
                if (comparison != 0) {
                    // FIXME: is this under txn? Does the order matter?
                    dbHolder.description2entry.remove(oldDescriptionKey.toByteArray());
                    dbHolder.description2entry.put(newDescriptionKey.toByteArray(), entryRecord);
                }
                dbHolder.db.commit();
                return;
            } catch (final OConcurrentModificationException e) {
                // Retry.
            } catch (final Exception e) {
                dbHolder.db.rollback();
                throw adaptException(e);
            }
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws LdapException {
        final DbHolder dbHolder = threadLocalDb.get();
        try {
            final byte[] descriptionKey = encodeDescription(description).toByteArray();
            final ORecordId id = (ORecordId) dbHolder.description2entry.get(descriptionKey);
            final ORecordBytes entryRecord = dbHolder.db.getRecord(id);
            return decodeEntry(entryRecord.toStream());
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

    @Override
    public Entry readEntryByDN(final DN name) throws LdapException {
        final DbHolder dbHolder = threadLocalDb.get();
        try {
            final byte[] dnKey = encodeDn(name).toByteArray();
            final ORecordId id = (ORecordId) dbHolder.dn2entry.get(dnKey);
            final ORecordBytes entryRecord = dbHolder.db.getRecord(id);
            return decodeEntry(entryRecord.toStream());
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

}
