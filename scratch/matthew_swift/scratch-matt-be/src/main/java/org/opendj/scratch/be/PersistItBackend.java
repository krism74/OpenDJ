package org.opendj.scratch.be;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

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

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.opendj.scratch.be.Util.*;

@SuppressWarnings("javadoc")
public final class PersistItBackend extends AbstractBackend {

    private static final File DB_DIR = new File("target/persistItBackend");

    public PersistItBackend() {
        super(new PersistItStorage());
    }

    private static final class PersistItStorage implements Storage {

        private Persistit db;
        private Map<TreeName, Volume> volumes = new HashMap<TreeName, Volume>();
        private Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();
        private Properties properties;

        /** {@inheritDoc} */
        @Override
        public void initialize(Map<String, String> options) {
            properties = new Properties();
            properties.setProperty("datapath", DB_DIR.toString());
            properties.setProperty("logpath", DB_DIR.toString());
            properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
            properties.setProperty("buffer.count.16384", "64K");
            properties.setProperty("journalpath", "${datapath}/dj_journal");
        }

        /** {@inheritDoc} */
        @Override
        public void open() {
            final List<TreeName> volumes = Collections.singletonList(TreeName.of("dj"));
            try {
                final Properties props = new Properties(properties);
                setVolumeProperties(props, volumes);

                db = new Persistit(props);
                db.initialize();

                for (TreeName volume : volumes) {
                    this.volumes.put(volume, db.loadVolume(volume.toString()));
                }
            } catch (PersistitException e) {
                throw new RuntimeException(e);
            }
        }

        private void setVolumeProperties(final Properties properties, List<TreeName> volumes) {
            for (int i = 0; i < volumes.size(); i++) {
                final TreeName volume = volumes.get(i);
                properties.setProperty("volume." + i, "${datapath}/" + volume + ",create,pageSize:16K,"
                        + "initialSize:50M,extensionSize:1M,maximumSize:10G");
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
                volumes.clear();
                trees.clear();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void createTree(TreeName treeName, Comparator<ByteSequence> comparator) {
            try {
                final String name = treeName.toString();
                if (trees.get(name) == null) {
                    final Volume volume = volumes.get(treeName.getSuffix());
                    final Tree newTree = volume.getTree(name, true);
                    trees.put(treeName, newTree);
                }
            } catch (PersistitException e) {
                throw new IllegalStateException(e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void deleteTrees(TreeName treeName) {
            try {
                for (Map.Entry<TreeName, Tree> entry : this.trees.entrySet()) {
                    if (treeName.isSuffixOf(entry.getKey())) {
                        getExchange(entry.getKey()).removeTree();
                    }
                }
            } catch (PersistitException e) {
                throw new IllegalStateException(e);
            }
        }

        private Exchange getExchange(TreeName treeName) throws PersistitException {
            final Volume volume = volumes.get(treeName);
            return db.getExchange(volume, treeName.toString(), false);
        }

        /** {@inheritDoc} */
        @Override
        public Importer startImport() {
            clearAndCreateDbDir(DB_DIR);
            return new PersistItImporter(this);
        }

        /** {@inheritDoc} */
        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            final PersistItTxn txn = new PersistItTxn(this);
            for (;;) {
                txn.begin();
                try {
                    return readTransaction.run(txn);
                } catch (RollbackException e) {
                    // Retry.
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
            final PersistItTxn txn = new PersistItTxn(this);
            for (;;) {
                txn.begin();
                try {
                    updateTransaction.run(txn);
                    txn.commit();
                    return;
                } catch (RollbackException e) {
                    // Retry.
                } catch (Exception e) {
                    txn.rollback();
                    throw e;
                } finally {
                    txn.end();
                }
            }
        }
    }

    private static final class PersistItImporter implements Importer {

        private final PersistItStorage storage;
        private final TreeBuilder importer;
        private final Key importKey;
        private final Value importValue;

        public PersistItImporter(PersistItStorage storage) {
            this.storage = storage;
            this.importer = new TreeBuilder(storage.db);
            this.importKey = new Key(storage.db);
            this.importValue = new Value(storage.db);
        }

        /** {@inheritDoc} */
        @Override
        public void put(TreeName treeName, ByteString key, ByteString value) {
            try {
                final Tree tree = storage.trees.get(treeName.toString());
                byte[] keyBytes = key.toByteArray();
                importKey.clear().appendByteArray(keyBytes, 0, keyBytes.length);
                importValue.clear().putByteArray(value.toByteArray());
                importer.store(tree, importKey, importValue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void close() {
            try {
                importer.merge();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                storage.close();
            }
        }
    }

    private static final class PersistItTxn implements UpdateTxn {

        private final PersistItStorage storage;
        private final Transaction txn;
        private Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

        public PersistItTxn(PersistItStorage storage) {
            this.storage = storage;
            this.txn = storage.db.getTransaction();
        }

        public void begin() throws PersistitException {
            txn.begin();
        }

        public void end() {
            releaseAllExchanges();
            txn.end();
        }

        public void commit() throws PersistitException {
            txn.commit();
        }

        public void rollback() {
            txn.rollback();
        }

        private Exchange getExchange(TreeName treeName) throws PersistitException {
            Exchange exchange = exchanges.get(treeName);
            if (exchange == null) {
                exchange = storage.getExchange(treeName);
                exchanges.put(treeName, exchange);
            }
            return exchange;
        }

        private void releaseAllExchanges() {
            for (Exchange ex : exchanges.values()) {
                storage.db.releaseExchange(ex);
            }
        }

        /** {@inheritDoc} */
        @Override
        public ByteString get(TreeName treeName, ByteString key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.fetch();
                final Value value = ex.getValue();
                if (!value.isDefined()) {
                    throw new RuntimeException(newLdapException(ResultCode.NO_SUCH_OBJECT));
                }
                return ByteString.wrap(value.getByteArray());
            } catch (PersistitException e) {
                throw new RuntimeException(e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public ByteString getRMW(TreeName treeName, ByteString key) {
            return get(treeName, key);
        }

        /** {@inheritDoc} */
        @Override
        public void put(TreeName treeName, ByteString key, ByteString value) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.getValue().clear().putByteArray(value.toByteArray());
                ex.store();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void remove(TreeName treeName, ByteString key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.remove();
            } catch (PersistitException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
