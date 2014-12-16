package org.opendj.scratch.be.impl;

import static com.sleepycat.je.OperationStatus.SUCCESS;
import static org.forgerock.util.Utils.closeSilently;
import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

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

@SuppressWarnings("javadoc")
public final class JeStorage implements Storage {
    private final class ImporterImpl implements Importer {
        private final DatabaseEntry importKey = new DatabaseEntry();
        private final DatabaseEntry importValue = new DatabaseEntry();

        @Override
        public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
            setData(importKey, key);
            setData(importValue, value);
            getTree(treeName).put(null, importKey, importValue);
        }

        @Override
        public void close() {
            JeStorage.this.close();
        }

        @Override
        public void createTree(TreeName treeName) {
            trees.put(treeName, env.openDatabase(null, treeName.toString(), dbConfig));
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final Transaction txn;
        private final DatabaseEntry dbKey = new DatabaseEntry();
        private final DatabaseEntry dbValue = new DatabaseEntry();

        private StorageImpl(Transaction txn) {
            this.txn = txn;
        }

        @Override
        public void create(TreeName treeName, ByteSequence key, ByteSequence value) {
            setData(dbKey, key);
            setData(dbValue, value);
            getTree(treeName).put(txn, dbKey, dbValue);
        }

        @Override
        public ByteString read(TreeName treeName, ByteSequence key) {
            setData(dbKey, key);
            setData(dbValue, null);
            if (getTree(treeName).get(txn, dbKey, dbValue, LockMode.READ_COMMITTED) == SUCCESS) {
                return ByteString.wrap(dbValue.getData());
            }
            return null;
        }

        @Override
        public void update(TreeName treeName, ByteSequence key, UpdateFunction f) {
            final Database db = getTree(treeName);
            setData(dbKey, key);
            for (;;) {
                setData(dbValue, f.computeNewValue(null));
                if (db.putNoOverwrite(txn, dbKey, dbValue) == SUCCESS) {
                    return;
                }
                setData(dbValue, null);
                final OperationStatus status = db.get(txn, dbKey, dbValue, LockMode.RMW);
                if (status == SUCCESS) {
                    final ByteString oldValue = ByteString.wrap(dbValue.getData());
                    setData(dbValue, f.computeNewValue(oldValue));
                    db.put(txn, dbKey, dbValue);
                    return;
                }
                // NOT FOUND.
            }
        }

        @Override
        public void delete(TreeName treeName, ByteSequence key) {
            setData(dbKey, key);
            getTree(treeName).delete(txn, dbKey);
        }
    }

    private static final File DB_DIR = new File("target/jeBackend");
    private final Map<TreeName, Database> trees = new HashMap<TreeName, Database>();
    private Environment env;
    private DatabaseConfig dbConfig;

    @Override
    public void initialize(Map<String, String> options) {
        // No op
    }

    @Override
    public void open() {
        open(false);
    }

    @Override
    public void openTree(TreeName treeName) {
        trees.put(treeName, env.openDatabase(null, treeName.toString(), dbConfig));
    }

    private void open(final boolean isImport) {
        final EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(!isImport);
        envConfig.setAllowCreate(true);
        envConfig.setLockTimeout(0, TimeUnit.MICROSECONDS);
        envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
        envConfig.setCachePercent(60);
        envConfig.setConfigParam(EnvironmentConfig.LOCK_N_LOCK_TABLES, String.valueOf("97"));
        envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_CACHE_SIZE, "10000");
        envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String.valueOf(1024 * 1024 * 100));
        env = new Environment(DB_DIR, envConfig);
        dbConfig =
                new DatabaseConfig().setAllowCreate(isImport).setKeyPrefixing(true)
                        .setTransactional(!isImport).setDeferredWrite(isImport);
        trees.clear();
    }

    @Override
    public void close() {
        closeSilently(trees.values());
        closeSilently(env);
        trees.clear();
    }

    private Database getTree(TreeName treeName) {
        return trees.get(treeName);
    }

    @Override
    public Importer startImport() {
        clearAndCreateDbDir(DB_DIR);
        open(true);
        return new ImporterImpl();
    }

    @Override
    public <T> T read(ReadOperation<T> operation) throws Exception {
        final Transaction txn = env.beginTransaction(null, new TransactionConfig());
        try {
            T result = operation.run(new StorageImpl(txn));
            txn.commit();
            return result;
        } finally {
            txn.abort();
        }
    }

    @Override
    public void write(WriteOperation operation) throws Exception {
        final Transaction txn = env.beginTransaction(null, new TransactionConfig());
        try {
            operation.run(new StorageImpl(txn));
            txn.commit();
        } finally {
            txn.abort();
        }
    }

    private void setData(DatabaseEntry dbEntry, ByteSequence bs) {
        dbEntry.setData(bs != null ? bs.toByteArray() : null);
    }
}
