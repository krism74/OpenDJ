package org.opendj.scratch.be;

import static com.sleepycat.je.OperationStatus.SUCCESS;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

@SuppressWarnings("javadoc")
public final class JEBackend extends Backend {

    public JEBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private final class ImporterImpl implements Importer {
            private final DatabaseEntry importKey = new DatabaseEntry();
            private final DatabaseEntry importValue = new DatabaseEntry();

            @Override
            public void put(TreeName treeName, ByteString key, ByteString value) {
                setData(importKey, key);
                setData(importValue, value);
                getTree(treeName).put(null, importKey, importValue);
            }

            @Override
            public void close() {
                StorageImpl.this.close();
            }

            @Override
            public void createTree(TreeName name, Comparator<ByteSequence> comparator) {
                // TODO: how do we set the comparator?
                trees.put(name, env.openDatabase(null, name.toString(), dbConfig));
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final DatabaseEntry txnKey = new DatabaseEntry();
            private final DatabaseEntry txnValue = new DatabaseEntry();
            private Transaction txn = env.beginTransaction(null, new TransactionConfig());

            private void commit() {
                txn.commit();
            }

            private void rollback() {
                txn.abort();
            }

            @Override
            public ByteString get(TreeName treeName, ByteString key) {
                return get0(treeName, key, LockMode.READ_COMMITTED);
            }

            @Override
            public ByteString getRMW(TreeName treeName, ByteString key) {
                return get0(treeName, key, LockMode.RMW);
            }

            private ByteString get0(TreeName treeName, ByteString key, LockMode lockMode) {
                setData(txnKey, key);
                setData(txnValue, null);
                if (getTree(treeName).get(txn, txnKey, txnValue, lockMode) == SUCCESS) {
                    return ByteString.wrap(txnValue.getData());
                }
                return null;
            }

            @Override
            public void put(TreeName treeName, ByteString key, ByteString value) {
                setData(txnKey, key);
                setData(txnValue, value);
                getTree(treeName).put(txn, txnKey, txnValue);
            }

            @Override
            public boolean remove(TreeName treeName, ByteString key) {
                setData(txnKey, key);
                return getTree(treeName).delete(txn, txnKey) == SUCCESS;
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
        public void openTree(TreeName name, Comparator<ByteSequence> comparator) {
            trees.put(name, env.openDatabase(null, name.toString(), dbConfig));
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
            envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String
                    .valueOf(1024 * 1024 * 100));
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

        private Database getTree(TreeName name) {
            return trees.get(name);
        }

        @Override
        public Importer startImport() {
            clearAndCreateDbDir(DB_DIR);
            open(true);
            return new ImporterImpl();
        }

        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            final TxnImpl txn = new TxnImpl();
            try {
                T result = readTransaction.run(txn);
                txn.commit();
                return result;
            } finally {
                txn.rollback();
            }
        }

        @Override
        public void update(UpdateTransaction updateTransaction) throws Exception {
            final TxnImpl txn = new TxnImpl();
            try {
                updateTransaction.run(txn);
                txn.commit();
            } finally {
                txn.rollback();
            }
        }

        private void setData(DatabaseEntry dbEntry, ByteString bs) {
            dbEntry.setData(bs != null ? bs.toByteArray() : null);
        }
    }

}
