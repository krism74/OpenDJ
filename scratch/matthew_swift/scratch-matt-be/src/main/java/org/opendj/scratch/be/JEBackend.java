package org.opendj.scratch.be;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

import static com.sleepycat.je.OperationStatus.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.opendj.scratch.be.Util.*;

@SuppressWarnings("javadoc")
public final class JEBackend extends AbstractBackend {

    public JEBackend() {
        super(new JEStorage());
    }

    private static final class JEStorage implements Storage {

        private static final File DB_DIR = new File("target/jeBackend");
        private Environment env;
        private Map<TreeName, Database> trees = new HashMap<TreeName, Database>();
        private DatabaseConfig dbConfig;

        /** {@inheritDoc} */
        @Override
        public void initialize(Map<String, String> options) {
            // No op
        }

        /** {@inheritDoc} */
        @Override
        public void open() {
            open(false);
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

            dbConfig = new DatabaseConfig().setAllowCreate(true).setKeyPrefixing(true)
                .setTransactional(!isImport).setDeferredWrite(isImport);
        }

        /** {@inheritDoc} */
        @Override
        public void close() {
            Utils.closeSilently(trees.values());
            trees.clear();
            if (env != null) {
                env.close();
                env = null;
            }
        }

        Database getTree(TreeName name) {
            return trees.get(name);
        }

        /** {@inheritDoc} */
        @Override
        public void createTree(TreeName treeName, Comparator<ByteSequence> comparator) {
            final Database db = env.openDatabase(null, treeName.toString(), dbConfig);
            trees.put(treeName, db);
        }

        /** {@inheritDoc} */
        @Override
        public void deleteTrees(TreeName treeName) {
            for (Map.Entry<TreeName, Database> entry : this.trees.entrySet()) {
                if (treeName.isSuffixOf(entry.getKey())) {
                    entry.getValue().close();
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public Importer startImport() {
            clearAndCreateDbDir(DB_DIR);
            open(true);
            return new JEImporter(this);
        }

        /** {@inheritDoc} */
        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            final JETxn txn = new JETxn(this);
            for (;;) {
                txn.begin();
                try {
                    return readTransaction.run(txn);
                } catch (Exception e) {
                    throw e;
                } finally {
                    txn.end();
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public void update(UpdateTransaction updateTransaction) throws Exception {
            final JETxn txn = new JETxn(this);
            for (;;) {
                txn.begin();
                try {
                    updateTransaction.run(txn);
                    txn.commit();
                    return;
                } catch (Exception e) {
                    txn.rollback();
                    throw e;
                } finally {
                    txn.end();
                }
            }
        }
    }

    private static final class JEImporter implements Importer {

        private final JEStorage storage;
        private final DatabaseEntry importKey = new DatabaseEntry();
        private final DatabaseEntry importValue = new DatabaseEntry();

        public JEImporter(JEStorage storage) {
            this.storage = storage;
        }

          /** {@inheritDoc} */
        @Override
        public void put(TreeName treeName, ByteString key, ByteString value) {
            setData(importKey, key);
            setData(importValue, value);
            storage.getTree(treeName).put(null, importKey, importValue);
        }

        /** {@inheritDoc} */
        @Override
        public void close() {
            storage.close();
        }
    }

    private static final class JETxn implements UpdateTxn {

        private final JEStorage storage;
        private final DatabaseEntry txnKey = new DatabaseEntry();
        private final DatabaseEntry txnValue = new DatabaseEntry();
        private Transaction txn;

        public JETxn(JEStorage storage) {
            this.storage = storage;
        }

        public void begin() {
            final TransactionConfig config = new TransactionConfig();
            this.txn = storage.env.beginTransaction(null, config);
        }

        public void commit() {
            txn.commit();
        }

        public void rollback() {
            txn.abort();
        }

        public void end() {
            // no op
        }

        /** {@inheritDoc} */
        @Override
        public ByteString get(TreeName treeName, ByteString key) {
            return get0(treeName, key, LockMode.READ_COMMITTED);
        }

        /** {@inheritDoc} */
        @Override
        public ByteString getRMW(TreeName treeName, ByteString key) {
            return get0(treeName, key, LockMode.RMW);
        }

        private ByteString get0(TreeName treeName, ByteString key, LockMode lockMode) {
            setData(txnKey, key);
            setData(txnValue, null);
            if (storage.getTree(treeName).get(txn, txnKey, txnValue, lockMode) != SUCCESS) {
                throw newRuntimeLdapException(ResultCode.NO_SUCH_OBJECT);
            }
            return ByteString.wrap(txnValue.getData());
        }

        /** {@inheritDoc} */
        @Override
        public void put(TreeName treeName, ByteString key, ByteString value) {
            setData(txnKey, key);
            setData(txnValue, value);
            if (storage.getTree(treeName).put(txn, txnKey, txnValue) != SUCCESS) {
                throw newRuntimeLdapException(ResultCode.NO_SUCH_OBJECT);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void remove(TreeName treeName, ByteString key) {
            setData(txnKey, key);
            if (storage.getTree(treeName).delete(txn, txnKey) != SUCCESS) {
                throw newRuntimeLdapException(ResultCode.NO_SUCH_OBJECT);
            }
        }

        private RuntimeException newRuntimeLdapException(ResultCode resultCode) {
            return new RuntimeException(newLdapException(resultCode));
        }
    }

    private static void setData(DatabaseEntry dbEntry, ByteString bs) {
        dbEntry.setData(bs != null ? bs.toByteArray() : null);
    }

}
