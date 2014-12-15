package org.opendj.scratch.be.impl;

import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

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
public final class OrientStorage implements Storage {

    private static String orientName(final TreeName treeName) {
        return treeName.toString().replaceAll(",", "&");
    }

    private static final class DbHolder {
        private final ODatabaseDocumentTx db;
        private final Map<TreeName, OIndex<?>> trees = new HashMap<TreeName, OIndex<?>>();

        private DbHolder(final ODatabaseDocumentTx db) {
            this.db = db;
        }

        private OIndex<?> getTree(final TreeName treeName) {
            OIndex<?> tree = trees.get(treeName);
            if (tree != null) {
                return tree;
            }
            tree = db.getMetadata().getIndexManager().getIndex(orientName(treeName));
            trees.put(treeName, tree);
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
        public void createTree(final TreeName treeName) {
            final OIndex<?> tree =
                    db.getMetadata().getIndexManager().createIndex(orientName(treeName), "UNIQUE",
                            new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID), null,
                            null, null);
            trees.put(treeName, tree);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            final ORecordBytes valueRecord = new ORecordBytes(db, value.toByteArray());
            valueRecord.save();
            trees.get(treeName).put(key.toByteArray(), valueRecord);
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final DbHolder dbHolder;

        private StorageImpl(final DbHolder dbHolder) {
            this.dbHolder = dbHolder;
        }

        @Override
        public ByteString get(final TreeName treeName, final ByteSequence key) {
            final ORecordId id = (ORecordId) dbHolder.getTree(treeName).get(key.toByteArray());
            final ORecordBytes record = dbHolder.db.getRecord(id);
            return ByteString.wrap(record.toStream());
        }

        @Override
        public ByteString getRMW(final TreeName treeName, final ByteSequence key) {
            return get(treeName, key);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            final ORecordId id = (ORecordId) dbHolder.getTree(treeName).get(key.toByteArray());
            final ORecordBytes record = dbHolder.db.getRecord(id);
            record.setDirty();
            record.fromStream(value.toByteArray());
            record.save();
        }

        @Override
        public boolean remove(final TreeName treeName, final ByteSequence key) {
            return dbHolder.getTree(treeName).remove(key.toByteArray());
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
    public void initialize(final Map<String, String> options) throws Exception {
        // OGlobalConfiguration.CACHE_LEVEL2_ENABLED.setValue(true);
        // OGlobalConfiguration.CACHE_LEVEL2_SIZE.setValue(100000);
    }

    @Override
    public void open() throws Exception {
        // No op
    }

    @Override
    public void openTree(final TreeName treeName) {
        // No op
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        return operation.run(new StorageImpl(threadLocalDb.get()));
    }

    @Override
    public Importer startImport() throws Exception {
        clearAndCreateDbDir(DB_DIR);
        return new ImporterImpl();
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        final DbHolder dbHolder = threadLocalDb.get();
        for (;;) {
            try {
                dbHolder.db.begin(TXTYPE.OPTIMISTIC);
                operation.run(new StorageImpl(dbHolder));
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
