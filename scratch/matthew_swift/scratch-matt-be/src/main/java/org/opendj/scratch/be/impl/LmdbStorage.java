package org.opendj.scratch.be.impl;

import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.fusesource.hawtjni.runtime.JNIEnv;
import org.fusesource.lmdbjni.Constants;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.Env;
import org.fusesource.lmdbjni.Transaction;
import org.mapdb.TxRollbackException;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

@SuppressWarnings("javadoc")
public final class LmdbStorage implements Storage {

    private final class ImporterImpl implements Importer {
        private final Env env;
        private final Map<TreeName, Database> trees = new HashMap<TreeName, Database>();
        private final Transaction txn;

        public ImporterImpl() {
            env = new Env();
            env.setMapSize(20L * 1024L * 1024L * 1024L);
            env.setMaxDbs(16);
            env.open(DB_DIR);

            txn = env.createTransaction(false);
        }

        @Override
        public void createTree(final TreeName treeName) {
            System.out.println("Creating " + treeName);
            trees.put(treeName, env.openDatabase(txn, treeName.toString(), Constants.CREATE));
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(txn, key.toByteArray(), value.toByteArray());
        }

        @Override
        public void close() {
            txn.commit();
            for (Database tree : trees.values()) {
                tree.close();
            }
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final Env env;
        private final Transaction tx;
        private final Map<TreeName, Database> trees = new HashMap<TreeName, Database>();

        private StorageImpl(final Env env, final Transaction tx) {
            this.env = env;
            this.tx = tx;
        }

        @Override
        public void create(TreeName treeName, ByteSequence key, ByteSequence value) {
            getTree(treeName).put(tx, key.toByteArray(), value.toByteArray());
        }

        @Override
        public ByteString read(final TreeName treeName, final ByteSequence key) {
            return ByteString.wrap(getTree(treeName).get(tx, key.toByteArray()));
        }

        @Override
        public void update(TreeName treeName, ByteSequence key, UpdateFunction f) {
            final Database tree = getTree(treeName);
            final byte[] kb = key.toByteArray();
            final byte[] vb = f.computeNewValue(null).toByteArray();
            final byte[] ovb = tree.put(tx, kb, vb);
            if (ovb != null) {
                final byte[] nvb = f.computeNewValue(ByteString.wrap(ovb)).toByteArray();
                tree.put(tx, kb, nvb);
            }
        }

        @Override
        public void delete(final TreeName treeName, final ByteSequence key) {
            getTree(treeName).delete(key.toByteArray());
        }

        private Database getTree(final TreeName treeName) {
            Database tree = trees.get(treeName);
            if (tree != null) {
                return tree;
            }
            tree = env.openDatabase(tx, treeName.toString(), 0);
            trees.put(treeName, tree);

            return tree;
        }
    }

    private static final String DB_DIR = "/media/ylecaillez/ForgeRock/test/";
    private Map<String, String> options;
    private Env env;;

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        this.options = options;
    }

    @Override
    public Importer startImport() throws Exception {
        clearAndCreateDbDir(new File(DB_DIR));
        return new ImporterImpl();
    }

    @Override
    public void open() throws Exception {
        env = new Env();
        env.setMaxDbs(16);
        env.setMapSize(20 * 1024 * 1024 * 1024);
        env.open(DB_DIR);
    }

    @Override
    public void openTree(final TreeName treeName) {
        // Nothing to do. Trees are opened for each txn.
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        final Transaction tx = env.createTransaction(true);
        try {
            return operation.run(new StorageImpl(env, tx));
        } catch (final TxRollbackException e) {
            e.printStackTrace();
            throw e;
            // try again
        } finally {
            tx.commit();
        }
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        final Transaction txn = env.createTransaction(false);
        try {
            operation.run(new StorageImpl(env, txn));
            txn.commit();
            return;
        } catch (final TxRollbackException e) {
            e.printStackTrace();
            throw e;
            // try again
        } catch (final Exception e) {
            txn.abort();
            throw e;
        }
    }

    @Override
    public void close() {
        env.close();
    }
}
