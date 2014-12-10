package org.opendj.scratch.be;

import static org.forgerock.util.Utils.closeSilently;
import static org.opendj.scratch.be.MemoryBackend.SERIALIZER;
import static org.opendj.scratch.be.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.TxMaker;
import org.mapdb.TxRollbackException;

@SuppressWarnings("javadoc")
public final class MapDBBackend extends AbstractBackend {
    private static final class StorageImpl implements Storage {
        private final class ImporterImpl implements Importer {
            private final DB db = DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported()
                    .asyncWriteEnable().commitFileSyncDisable().transactionDisable()
                    .closeOnJvmShutdown().make();
            private final Map<TreeName, Map<ByteString, ByteString>> trees =
                    new HashMap<TreeName, Map<ByteString, ByteString>>();

            @Override
            public void createTree(final TreeName name, final Comparator<ByteSequence> comparator) {
                final ConcurrentNavigableMap<ByteString, ByteString> tree =
                        db.createTreeMap(name.toString()).comparator(comparator).keySerializer(
                                SERIALIZER).valueSerializer(SERIALIZER).make();
                trees.put(name, tree);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                trees.get(name).put(key, value);
            }

            @Override
            public void close() {
                db.close();
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final DB db;
            private final Map<TreeName, Map<ByteString, ByteString>> trees =
                    new HashMap<TreeName, Map<ByteString, ByteString>>();

            private TxnImpl(final DB db) {
                this.db = db;
            }

            @Override
            public ByteString get(final TreeName name, final ByteString key) {
                return getTree(name).get(key);
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return get(name, key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                getTree(name).put(key, value);
            }

            @Override
            public boolean remove(final TreeName name, final ByteString key) {
                return getTree(name).remove(key) != null;
            }

            private Map<ByteString, ByteString> getTree(final TreeName name) {
                Map<ByteString, ByteString> tree = trees.get(name);
                if (tree != null) {
                    return tree;
                }
                tree = db.getTreeMap(name.toString());
                trees.put(name, tree);
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
        public void openTree(final TreeName name, final Comparator<ByteSequence> comparator) {
            // Nothing to do. Trees are opened for each txn.
        }

        @Override
        public <T> T read(final ReadTransaction<T> readTransaction) throws Exception {
            for (;;) {
                final DB txn = txMaker.makeTx();
                try {
                    return readTransaction.run(new TxnImpl(txn));
                } catch (final TxRollbackException e) {
                    // try again
                } finally {
                    txn.close();
                }
            }
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            for (;;) {
                final DB txn = txMaker.makeTx();
                try {
                    updateTransaction.run(new TxnImpl(txn));
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

    public MapDBBackend() {
        super(new StorageImpl());
    }

}
