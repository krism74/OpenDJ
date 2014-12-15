package org.opendj.scratch.be.impl;

import static org.forgerock.util.Utils.closeSilently;
import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.TxMaker;
import org.mapdb.TxRollbackException;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

@SuppressWarnings("javadoc")
public final class MapDbStorage implements Storage {
    private final class ImporterImpl implements Importer {
        private final DB db = DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported()
                .asyncWriteEnable().commitFileSyncDisable().transactionDisable()
                .closeOnJvmShutdown().make();
        private final Map<TreeName, Map<byte[], byte[]>> trees =
                new HashMap<TreeName, Map<byte[], byte[]>>();

        @Override
        public void createTree(final TreeName treeName) {
            final ConcurrentNavigableMap<byte[], byte[]> tree =
                    db.createTreeMap(treeName.toString()).keySerializer(BTreeKeySerializer.BYTE_ARRAY)
                            .valueSerializer(Serializer.BYTE_ARRAY).make();
            trees.put(treeName, tree);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(key.toByteArray(), value.toByteArray());
        }

        @Override
        public void close() {
            db.close();
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final DB db;
        private final Map<TreeName, ConcurrentNavigableMap<byte[], byte[]>> trees =
                new HashMap<TreeName, ConcurrentNavigableMap<byte[], byte[]>>();

        private StorageImpl(final DB db) {
            this.db = db;
        }

        @Override
        public ByteString get(final TreeName treeName, final ByteSequence key) {
            return ByteString.wrap(getTree(treeName).get(key.toByteArray()));
        }

        @Override
        public ByteString getRMW(final TreeName treeName, final ByteSequence key) {
            return get(treeName, key);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            getTree(treeName).put(key.toByteArray(), value.toByteArray());
        }

        @Override
        public boolean putIfAbsent(TreeName treeName, ByteSequence key, ByteSequence value) {
            return getTree(treeName).putIfAbsent(key.toByteArray(), value.toByteArray()) == null;
        }

        @Override
        public boolean remove(final TreeName treeName, final ByteSequence key) {
            return getTree(treeName).remove(key) != null;
        }

        private ConcurrentNavigableMap<byte[], byte[]> getTree(final TreeName treeName) {
            ConcurrentNavigableMap<byte[], byte[]> tree = trees.get(treeName);
            if (tree != null) {
                return tree;
            }
            tree = db.getTreeMap(treeName.toString());
            trees.put(treeName, tree);
            return tree;
        }
    }

    private static final File DB_DIR = new File("target/mapBackend");
    private static final File DB_FILE = new File(DB_DIR, "db");
    private Map<String, String> options;
    private TxMaker txMaker;

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        this.options = options;
    }

    @Override
    public Importer startImport() throws Exception {
        clearAndCreateDbDir(DB_DIR);
        return new ImporterImpl();
    }

    @Override
    public void open() throws Exception {
        final boolean useCache = options.containsKey("useCache");
        final int cacheSize =
                options.containsKey("cacheSize") ? Integer.valueOf(options.get("cacheSize"))
                        : 32768;
        if (useCache) {
            txMaker =
                    DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().closeOnJvmShutdown()
                            .cacheSize(cacheSize).commitFileSyncDisable().makeTxMaker();
        } else {
            txMaker =
                    DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().closeOnJvmShutdown()
                            .cacheDisable().commitFileSyncDisable().makeTxMaker();
        }
    }

    @Override
    public void openTree(final TreeName treeName) {
        // Nothing to do. Trees are opened for each txn.
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        for (;;) {
            final DB txn = txMaker.makeTx();
            try {
                return operation.run(new StorageImpl(txn));
            } catch (final TxRollbackException e) {
                // try again
            } finally {
                txn.close();
            }
        }
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        for (;;) {
            final DB txn = txMaker.makeTx();
            try {
                operation.run(new StorageImpl(txn));
                txn.commit();
                return;
            } catch (final TxRollbackException e) {
                // try again
            } catch (final Exception e) {
                txn.rollback();
                throw e;
            } finally {
                txn.close();
            }
        }
    }

    @Override
    public void close() {
        closeSilently(txMaker);
    }
}
