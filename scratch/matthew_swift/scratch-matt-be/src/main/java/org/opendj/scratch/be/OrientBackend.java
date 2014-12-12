package org.opendj.scratch.be;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

import com.orientechnologies.common.serialization.types.OBinaryTypeSerializer;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.ORuntimeKeyIndexDefinition;
import com.orientechnologies.orient.core.intent.OIntentMassiveInsert;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.tx.OTransaction.TXTYPE;

@SuppressWarnings("javadoc")
public final class OrientBackend extends Backend {

    public OrientBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private static String orientName(final TreeName name) {
            return name.toString().replaceAll(",", "&");
        }

        private static final class DbHolder {
            private final ODatabaseDocumentTx db;
            private final Map<TreeName, OIndex<?>> trees = new HashMap<TreeName, OIndex<?>>();

            private DbHolder(final ODatabaseDocumentTx db) {
                this.db = db;
            }

            private OIndex<?> getTree(final TreeName name) {
                OIndex<?> tree = trees.get(name);
                if (tree != null) {
                    return tree;
                }
                tree = db.getMetadata().getIndexManager().getIndex(orientName(name));
                trees.put(name, tree);
                return tree;
            }
        }

        private final class ImporterImpl implements Importer {
            private final ODatabaseDocumentTx db;
            private final Map<TreeName, OIndex<?>> trees = new HashMap<TreeName, OIndex<?>>();

            public ImporterImpl() {
                db = new ODatabaseDocumentTx(DB_URL).create();
                db.declareIntent(new OIntentMassiveInsert());
            }

            @Override
            public void close() {
                db.close();
            }

            @Override
            public void createTree(final TreeName name) {
                final OIndex<?> tree =
                        db.getMetadata().getIndexManager().createIndex(orientName(name), "UNIQUE",
                                new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID),
                                null, null, null);
                trees.put(name, tree);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                final ORecordBytes valueRecord = new ORecordBytes(db, value.toByteArray());
                valueRecord.save();
                trees.get(name).put(key.toByteArray(), valueRecord);
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final DbHolder dbHolder;

            private TxnImpl(final DbHolder dbHolder) {
                this.dbHolder = dbHolder;
            }

            @Override
            public ByteString get(final TreeName name, final ByteString key) {
                final ORecordId id = (ORecordId) dbHolder.getTree(name).get(key.toByteArray());
                final ORecordBytes record = dbHolder.db.getRecord(id);
                return ByteString.wrap(record.toStream());
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return get(name, key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                final ORecordId id = (ORecordId) dbHolder.getTree(name).get(key.toByteArray());
                final ORecordBytes record = dbHolder.db.getRecord(id);
                record.setDirty();
                record.fromStream(value.toByteArray());
                record.save();
            }

            @Override
            public boolean remove(final TreeName name, final ByteString key) {
                return dbHolder.getTree(name).remove(key.toByteArray());
            }
        }

        private static final File DB_DIR = new File("target/orientBackend");
        private static final String DB_URL = "plocal:" + DB_DIR.getAbsolutePath();

        private final Queue<ODatabaseDocumentTx> activeDbConnections =
                new ConcurrentLinkedQueue<ODatabaseDocumentTx>();

        private final ThreadLocal<DbHolder> threadLocalDb = new ThreadLocal<DbHolder>() {
            @Override
            protected DbHolder initialValue() {
                final ODatabaseDocumentTx db =
                        new ODatabaseDocumentTx(DB_URL).open("admin", "admin");
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
        public void initialize(final Map<String, String> options) throws Exception {
            // OGlobalConfiguration.CACHE_LEVEL2_ENABLED.setValue(true);
            // OGlobalConfiguration.CACHE_LEVEL2_SIZE.setValue(100000);
        }

        @Override
        public void open() throws Exception {
            // No op
        }

        @Override
        public void openTree(final TreeName name, final Comparator<ByteSequence> comparator) {
            // No op
        }

        @Override
        public <T> T read(final ReadTransaction<T> readTransaction) throws Exception {
            return readTransaction.run(new TxnImpl(threadLocalDb.get()));
        }

        @Override
        public Importer startImport() throws Exception {
            clearAndCreateDbDir(DB_DIR);
            return new ImporterImpl();
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            final DbHolder dbHolder = threadLocalDb.get();
            for (;;) {
                try {
                    dbHolder.db.begin(TXTYPE.OPTIMISTIC);
                    updateTransaction.run(new TxnImpl(dbHolder));
                    dbHolder.db.commit();
                    return;
                } catch (final OConcurrentModificationException e) {
                    // Retry.
                } catch (final Exception e) {
                    dbHolder.db.rollback();
                }
            }
        }
    }
}
