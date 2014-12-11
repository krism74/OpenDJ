package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.TreeBuilder;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

@SuppressWarnings("javadoc")
public final class PersistItBackend extends Backend {

    public PersistItBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private final class ImporterImpl implements Importer {
            private final Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();
            private final TreeBuilder importer = new TreeBuilder(db);
            private final Key importKey = new Key(db);
            private final Value importValue = new Value(db);

            @Override
            public void createTree(TreeName treeName, Comparator<ByteSequence> comparator) {
                try {
                    // FIXME: how do we set the comparator?
                    final Tree tree = volume.getTree(treeName.toString(), true);
                    trees.put(treeName, tree);
                } catch (PersistitException e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public void put(TreeName treeName, ByteString key, ByteString value) {
                try {
                    final Tree tree = trees.get(treeName.toString());
                    byte[] keyBytes = key.toByteArray();
                    importKey.clear().appendByteArray(keyBytes, 0, keyBytes.length);
                    importValue.clear().putByteArray(value.toByteArray());
                    importer.store(tree, importKey, importValue);
                } catch (Exception e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public void close() {
                try {
                    importer.merge();
                } catch (Exception e) {
                    throw new StorageRuntimeException(e);
                } finally {
                    StorageImpl.this.close();
                }
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final Transaction txn = db.getTransaction();
            private final Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

            private void begin() throws PersistitException {
                txn.begin();
            }

            private void end() {
                releaseAllExchanges();
                txn.end();
            }

            private void commit() throws PersistitException {
                txn.commit();
            }

            private Exchange getExchange(TreeName treeName) throws PersistitException {
                Exchange exchange = exchanges.get(treeName);
                if (exchange == null) {
                    exchange = db.getExchange(volume, treeName.toString(), false);
                    exchanges.put(treeName, exchange);
                }
                return exchange;
            }

            private void releaseAllExchanges() {
                for (Exchange ex : exchanges.values()) {
                    db.releaseExchange(ex);
                }
            }

            @Override
            public ByteString get(TreeName treeName, ByteString key) {
                try {
                    final Exchange ex = getExchange(treeName);
                    ex.getKey().clear().append(key.toByteArray());
                    ex.fetch();
                    final Value value = ex.getValue();
                    if (value.isDefined()) {
                        return ByteString.wrap(value.getByteArray());
                    }
                    return null;
                } catch (PersistitException e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public ByteString getRMW(TreeName treeName, ByteString key) {
                return get(treeName, key);
            }

            @Override
            public void put(TreeName treeName, ByteString key, ByteString value) {
                try {
                    final Exchange ex = getExchange(treeName);
                    ex.getKey().clear().append(key.toByteArray());
                    ex.getValue().clear().putByteArray(value.toByteArray());
                    ex.store();
                } catch (Exception e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public boolean remove(TreeName treeName, ByteString key) {
                try {
                    final Exchange ex = getExchange(treeName);
                    ex.getKey().clear().append(key.toByteArray());
                    return ex.remove();
                } catch (PersistitException e) {
                    throw new StorageRuntimeException(e);
                }
            }
        }

        private static final File DB_DIR = new File("target/persistItBackend");
        private Persistit db;
        private Volume volume;
        private Properties properties;

        @Override
        public void initialize(Map<String, String> options) {
            properties = new Properties();
            properties.setProperty("datapath", DB_DIR.toString());
            properties.setProperty("logpath", DB_DIR.toString());
            properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
            properties.setProperty("buffer.count.16384", "64K");
            properties.setProperty("journalpath", "${datapath}/dj_journal");
            properties.setProperty("volume.1", "${datapath}/dj,create,pageSize:16K,"
                    + "initialSize:50M,extensionSize:1M,maximumSize:10G");
        }

        @Override
        public void open() {
            try {
                db = new Persistit(properties);
                db.initialize();
                volume = db.loadVolume(volume.toString());
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void close() {
            if (db != null) {
                try {
                    db.close();
                    db = null;
                } catch (PersistitException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public Importer startImport() {
            clearAndCreateDbDir(DB_DIR);
            open();
            return new ImporterImpl();
        }

        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            final TxnImpl txn = new TxnImpl();
            for (;;) {
                txn.begin();
                try {
                    return readTransaction.run(txn);
                } catch (RollbackException e) {
                    // Retry.
                } finally {
                    txn.end();
                }
            }
        }

        @Override
        public void update(UpdateTransaction updateTransaction) throws Exception {
            final TxnImpl txn = new TxnImpl();
            for (;;) {
                txn.begin();
                try {
                    updateTransaction.run(txn);
                    txn.commit();
                    return;
                } catch (RollbackException e) {
                    // Retry.
                } finally {
                    txn.end();
                }
            }
        }

        @Override
        public void openTree(TreeName name, Comparator<ByteSequence> comparator) {
            // Nothing to do.
        }
    }

}
