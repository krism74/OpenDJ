package org.opendj.scratch.be.impl;

import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.StorageRuntimeException;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

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
public final class PersistItStorage implements Storage {
    private final class ImporterImpl implements Importer {
        private final Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();
        private final TreeBuilder importer = new TreeBuilder(db);
        private final Key importKey = new Key(db);
        private final Value importValue = new Value(db);

        @Override
        public void createTree(TreeName treeName) {
            try {
                final Tree tree = volume.getTree(treeName.toString(), true);
                trees.put(treeName, tree);
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
            try {
                final Tree tree = trees.get(treeName);
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
                PersistItStorage.this.close();
            }
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

        private void release() {
            for (Exchange ex : exchanges.values()) {
                db.releaseExchange(ex);
            }
        }

        private Exchange getExchange(TreeName treeName) throws PersistitException {
            Exchange exchange = exchanges.get(treeName);
            if (exchange == null) {
                exchange = db.getExchange(volume, treeName.toString(), false);
                exchanges.put(treeName, exchange);
            }
            return exchange;
        }

        @Override
        public void create(TreeName treeName, ByteSequence key, ByteSequence value) {
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
        public ByteString read(TreeName treeName, ByteSequence key) {
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
        public void update(TreeName treeName, ByteSequence key, UpdateFunction f) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.fetch();
                final Value value = ex.getValue();
                final ByteSequence oldValue =
                        value.isDefined() ? ByteString.wrap(value.getByteArray()) : null;
                final ByteSequence newValue = f.computeNewValue(oldValue);
                ex.getValue().clear().putByteArray(newValue.toByteArray());
                ex.store();
            } catch (Exception e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void delete(TreeName treeName, ByteSequence key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.remove();
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
        properties.setProperty("appendonly", "false");
        properties.setProperty("datapath", DB_DIR.toString());
        properties.setProperty("logpath", DB_DIR.toString());
        properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
        properties.setProperty("buffer.count.16384", "64K");
        properties.setProperty("journalpath", "${datapath}/dj_journal");
        properties.setProperty("txnpolicy", "soft");
        properties.setProperty("volume.1", "${datapath}/dj,create,pageSize:16K,"
                + "initialSize:50M,extensionSize:1M,maximumSize:10G");
    }

    @Override
    public void open() {
        try {
            db = new Persistit(properties);
            db.initialize();
            db.getManagement().setAppendOnly(false);
            volume = db.loadVolume("dj");
        } catch (Exception e) {
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
    public <T> T read(ReadOperation<T> operation) throws Exception {
        final Transaction txn = db.getTransaction();
        for (;;) {
            txn.begin();
            try {
                final StorageImpl storageImpl = new StorageImpl();
                try {
                    final T result = operation.run(storageImpl);
                    txn.commit();
                    return result;
                } catch (StorageRuntimeException e) {
                    throw (Exception) e.getCause();
                } finally {
                    storageImpl.release();
                }
            } catch (RollbackException e) {
                // retry
            } catch (Exception e) {
                txn.rollback();
                throw e;
            } finally {
                txn.end();
            }
        }
    }

    @Override
    public void write(WriteOperation operation) throws Exception {
        final Transaction txn = db.getTransaction();
        for (;;) {
            txn.begin();
            try {
                final StorageImpl storageImpl = new StorageImpl();
                try {
                    operation.run(storageImpl);
                    txn.commit();
                    return;
                } catch (StorageRuntimeException e) {
                    throw (Exception) e.getCause();
                } finally {
                    storageImpl.release();
                }
            } catch (RollbackException e) {
                // retry
            } catch (Exception e) {
                txn.rollback();
                throw e;
            } finally {
                txn.end();
            }
        }
    }

    @Override
    public void openTree(TreeName treeName) {
        // Nothing to do.
    }
}
