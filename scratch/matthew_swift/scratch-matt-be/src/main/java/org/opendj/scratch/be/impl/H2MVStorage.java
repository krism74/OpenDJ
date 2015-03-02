package org.opendj.scratch.be.impl;

import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.db.TransactionStore;
import org.h2.mvstore.db.TransactionStore.Transaction;
import org.h2.mvstore.db.TransactionStore.TransactionMap;
import org.h2.mvstore.type.DataType;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

/**
 * A prototype backend using H2's MVStore.
 * Note that you need a version > 1.4.185 since this one has a bug
 * See https://code.google.com/p/h2database/source/detail?r=6036
 * <p>
 * See http://www.h2database.com/html/mvstore.html
 */
public final class H2MVStorage implements Storage {

    private static final class ByteSequenceDataType implements DataType {

        @Override
        public int compare(final Object a, final Object b) {
            return ((ByteSequence) a).compareTo(((ByteSequence) b));
        }

        @Override
        public int getMemory(final Object obj) {
            return 16 + ((ByteSequence) obj).length();
        }

        @Override
        public ByteString read(final ByteBuffer buff) {
            final int len = DataUtils.readVarInt(buff);
            return new ByteStringBuilder().append(buff, len).toByteString();
        }

        @Override
        public void read(final ByteBuffer buff, final Object[] obj, final int len, final boolean key) {
            for (int i = 0; i < len; i++) {
                obj[i] = read(buff);
            }
        }

        @Override
        public void write(final WriteBuffer buff, final Object obj) {
            buff.putVarInt(((ByteSequence) obj).length());
            buff.put(((ByteSequence) obj).toByteArray());
        }

        @Override
        public void write(final WriteBuffer buff, final Object[] obj, final int len, final boolean key) {
            for (int i = 0; i < len; i++) {
                write(buff, obj[i]);
            }
        }

    }

    private final class ImporterImpl implements Importer {

        private final Map<String, TransactionMap<ByteSequence, ByteSequence>> treeToMaps;
        private final Transaction tx;

        public ImporterImpl(Transaction tx) {
            this.tx = tx;
            this.treeToMaps = new HashMap<String, TransactionMap<ByteSequence, ByteSequence>>();
        }

        @Override
        public void close() {
            tx.commit();
            H2MVStorage.this.close();
        }

        @Override
        public void createTree(final TreeName treeName) {
            getMapForTree(treeName);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            getMapForTree(treeName).putCommitted(key, value);
        }

        private final TransactionMap<ByteSequence, ByteSequence> getMapForTree(TreeName aTreeName) {
            TransactionMap<ByteSequence, ByteSequence> map = treeToMaps.get(aTreeName.toString());
            if (map == null) {
                map = tx.openMap(aTreeName.toString(), BYTE_SEQUENCE_TYPE, BYTE_SEQUENCE_TYPE);
                treeToMaps.put(aTreeName.toString(), map);
            }

            return map;
        }
    }

    private final class StorageImpl implements WriteableStorage {

        private final Transaction tx;
        private final Map<String, TransactionMap<ByteSequence, ByteSequence>> treeToMaps;

        public StorageImpl(Transaction tx) {
            this.tx = tx;
            this.treeToMaps = new HashMap<String, TransactionMap<ByteSequence, ByteSequence>>();
        }

        @Override
        public void create(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            TransactionMap<ByteSequence, ByteSequence> map = getMapForTree(treeName);
            map.put(key, value);
        }

        @Override
        public void delete(final TreeName treeName, final ByteSequence key) {
            TransactionMap<ByteSequence, ByteSequence> map = getMapForTree(treeName);
            map.remove(key);
        }

        @Override
        public ByteString read(final TreeName treeName, final ByteSequence key) {
            TransactionMap<ByteSequence, ByteSequence> map = getMapForTree(treeName);
            return map.get(key).toByteString();
        }

        @Override
        public void update(final TreeName treeName, final ByteSequence key, final UpdateFunction f) {
            TransactionMap<ByteSequence, ByteSequence> map = getMapForTree(treeName);

            final ByteSequence vb = f.computeNewValue(null);
            final ByteSequence ovb = map.put(key, vb);
            if (ovb != null) {
                final ByteSequence nvb = f.computeNewValue(ovb);
                map.put(key, nvb);
            }
        }
        
        private final TransactionMap<ByteSequence, ByteSequence> getMapForTree(TreeName aTreeName) {
            TransactionMap<ByteSequence, ByteSequence> map = treeToMaps.get(aTreeName.toString());
            if (map == null) {
                map = tx.openMap(aTreeName.toString(), BYTE_SEQUENCE_TYPE, BYTE_SEQUENCE_TYPE);
                treeToMaps.put(aTreeName.toString(), map);
            }

            return map;
        }

    }

    private static final ByteSequenceDataType BYTE_SEQUENCE_TYPE = new ByteSequenceDataType();
    private static final File DB_DIR = new File("target/h2Backend");
    private static final File DB_FILE = new File(DB_DIR, "db");

    private MVStore store;
    private TransactionStore txStore;
    private final AtomicInteger nbOpenTx = new AtomicInteger();

    @Override
    public void close() {
        if (txStore != null) {
            txStore.close();
            txStore = null;
        }
        if (store != null) {
            store.close();
            store = null;
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        // Nothing to do.
    }

    @Override
    public void open() throws Exception {
        store = new MVStore.Builder().fileName(DB_FILE.toString()).cacheSize(2048).pageSplitSize(4*1024).open();

        txStore = new TransactionStore(store, BYTE_SEQUENCE_TYPE);
        txStore.init();
    }

    @Override
    public void openTree(final TreeName treeName) {
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        T data;

        Transaction tx = null;
        try {
            tx = txStore.begin();
            data = operation.run(new StorageImpl(tx));
            tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            if (tx != null && tx.getStatus() != Transaction.STATUS_CLOSED) {
                tx.rollback();
                nbOpenTx.decrementAndGet();
            }

        }

        return data;
    }

    @Override
    public Importer startImport() throws Exception {
        clearAndCreateDbDir(DB_DIR);
        open();

        Transaction tx = txStore.begin();
        return new ImporterImpl(tx);
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        Transaction tx = null;

        try {
            tx = txStore.begin();
            operation.run(new StorageImpl(tx));
            tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        } finally {
            if (tx != null && tx.getStatus() != Transaction.STATUS_CLOSED) {
                tx.rollback();
                nbOpenTx.decrementAndGet();

            }

        }
    }

}
