package org.opendj.scratch.be.impl;

import static jetbrains.exodus.env.StoreConfig.USE_EXISTING;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING;
import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
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
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.StorageRuntimeException;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

@SuppressWarnings("javadoc")
public final class XodusStorage implements Storage {
    private final class ImporterImpl implements Importer {
        private final Map<TreeName, Store> trees = new HashMap<TreeName, Store>();
        private final Transaction txn = env.beginTransaction();

        @Override
        public void createTree(final TreeName treeName) {
            final Store store =
                    env.openStore(treeName.toString(), WITHOUT_DUPLICATES_WITH_PREFIXING, txn);
            trees.put(treeName, store);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(txn, toByteIterable(key), toByteIterable(value));
        }

        @Override
        public void close() {
            txn.commit();
            XodusStorage.this.close();
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final Transaction txn;

        private StorageImpl(final Transaction txn) {
            this.txn = txn;
        }

        @Override
        public ByteString get(final TreeName treeName, final ByteSequence key) {
            return toByteString(trees.get(treeName).get(txn, toByteIterable(key)));
        }

        @Override
        public ByteString getRMW(final TreeName treeName, final ByteSequence key) {
            return get(treeName, key);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(txn, toByteIterable(key), toByteIterable(value));
        }

        @Override
        public boolean remove(final TreeName treeName, final ByteSequence key) {
            return trees.get(treeName).delete(txn, toByteIterable(key));
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
    public void openTree(final TreeName treeName) {
        env.executeInTransaction(new TransactionalExecutable() {
            @Override
            public void execute(final Transaction txn) {
                trees.put(treeName, env.openStore(treeName.toString(), USE_EXISTING, txn));
            }
        });
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        return env.computeInReadonlyTransaction(new TransactionalComputable<T>() {
            @Override
            public T compute(final Transaction txn) {
                try {
                    return operation.run(new StorageImpl(txn));
                } catch (final Exception e) {
                    throw new StorageRuntimeException(e);
                }
            }
        });
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        env.executeInTransaction(new TransactionalExecutable() {
            @Override
            public void execute(final Transaction txn) {
                try {
                    operation.run(new StorageImpl(txn));
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

    private ByteIterable toByteIterable(final ByteSequence value) {
        return value != null ? new ArrayByteIterable(value.toByteArray()) : null;
    }

    private ByteString toByteString(final ByteIterable value) {
        return value != null ? ByteString.wrap(value.getBytesUnsafe(), 0, value.getLength()) : null;
    }
}
