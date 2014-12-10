package org.opendj.scratch.be;

import static jetbrains.exodus.env.StoreConfig.USE_EXISTING;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING;
import static org.opendj.scratch.be.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.env.TransactionalComputable;
import jetbrains.exodus.env.TransactionalExecutable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public final class XodusBackend extends AbstractBackend {
    public XodusBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private final class ImporterImpl implements Importer {
            private final Map<TreeName, Store> trees = new HashMap<TreeName, Store>();
            private final Transaction txn = env.beginTransaction();

            @Override
            public void createTree(final TreeName name, final Comparator<ByteSequence> comparator) {
                env.executeInTransaction(new TransactionalExecutable() {
                    @Override
                    public void execute(final Transaction txn) {
                        // FIXME: set comparator.
                        final Store store =
                                env.openStore(name.toString(), WITHOUT_DUPLICATES_WITH_PREFIXING,
                                        txn);
                        trees.put(name, store);
                    }
                });
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                trees.get(name).put(null, toByteIterable(key), toByteIterable(value));
            }

            @Override
            public void close() {
                txn.commit();
                StorageImpl.this.close();
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final Transaction txn;

            private TxnImpl(final Transaction txn) {
                this.txn = txn;
            }

            @Override
            public ByteString get(final TreeName name, final ByteString key) {
                return toByteString(trees.get(name).get(txn, toByteIterable(key)));
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return get(name, key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                trees.get(name).put(txn, toByteIterable(key), toByteIterable(value));
            }

            @Override
            public void remove(final TreeName name, final ByteString key) {
                trees.get(name).delete(txn, toByteIterable(key));
            }
        }

        private static final File DB_DIR = new File("target/xodusBackend");
        private Environment env = null;
        private final Map<TreeName, Store> trees = new HashMap<TreeName, Store>();

        @Override
        public void initialize(final Map<String, String> options) throws Exception {
            // No op
        }

        @Override
        public Importer startImport() throws Exception {
            clearAndCreateDbDir(DB_DIR);
            open();
            return new ImporterImpl();
        }

        @Override
        public void open() throws Exception {
            final EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setLogFileSize(100 * 1024);
            envConfig.setLogCachePageSize(2 * 1024 * 1024);
            env = Environments.newInstance(DB_DIR, envConfig);
        }

        @Override
        public void openTree(final TreeName name, final Comparator<ByteSequence> comparator) {
            env.executeInTransaction(new TransactionalExecutable() {
                @Override
                public void execute(final Transaction txn) {
                    trees.put(name, env.openStore(name.toString(), USE_EXISTING, txn));
                }
            });
        }

        @Override
        public <T> T read(final ReadTransaction<T> readTransaction) throws Exception {
            return env.computeInReadonlyTransaction(new TransactionalComputable<T>() {
                @Override
                public T compute(final Transaction txn) {
                    try {
                        return readTransaction.run(new TxnImpl(txn));
                    } catch (final Exception e) {
                        throw new StorageRuntimeException(e);
                    }
                }
            });
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            env.executeInTransaction(new TransactionalExecutable() {
                @Override
                public void execute(final Transaction txn) {
                    try {
                        updateTransaction.run(new TxnImpl(txn));
                    } catch (final Exception e) {
                        throw new StorageRuntimeException(e);
                    }
                }
            });
        }

        @Override
        public void close() {
            if (env != null) {
                env.close();
                env = null;
                trees.clear();
            }
        }

        private ByteIterable toByteIterable(final ByteString value) {
            return value != null ? new ArrayByteIterable(value.toByteArray()) : null;
        }

        private ByteString toByteString(final ByteIterable value) {
            return value != null ? ByteString.wrap(value.getBytesUnsafe(), 0, value.getLength())
                    : null;
        }
    }
}
