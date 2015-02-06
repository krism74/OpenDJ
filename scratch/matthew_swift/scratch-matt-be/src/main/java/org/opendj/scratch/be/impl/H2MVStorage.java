package org.opendj.scratch.be.impl;

import static org.opendj.scratch.be.impl.Util.clearAndCreateDbDir;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.DataType;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

/**
 * A prototype backend using H2's MVStore. This implementation is quite
 * primitive because it does not use transactions in order to ensure updates are
 * atomic. In addition, no attempt has been made to tune it.
 * <p>
 * See http://www.h2database.com/html/mvstore.html
 */
public final class H2MVStorage implements Storage {
    private static final class ByteArrayDataType implements DataType {

        @Override
        public int compare(final Object a, final Object b) {
            return ByteString.BYTE_ARRAY_COMPARATOR.compare((byte[]) a, (byte[]) b);
        }

        @Override
        public int getMemory(final Object obj) {
            return 16 + ((byte[]) obj).length;
        }

        @Override
        public byte[] read(final ByteBuffer buff) {
            final int len = DataUtils.readVarInt(buff);
            final byte[] bytes = new byte[len];
            buff.get(bytes);
            return bytes;
        }

        @Override
        public void read(final ByteBuffer buff, final Object[] obj, final int len, final boolean key) {
            for (int i = 0; i < len; i++) {
                obj[i] = read(buff);
            }
        }

        @Override
        public void write(final WriteBuffer buff, final Object obj) {
            final byte[] bytes = (byte[]) obj;
            final int len = bytes.length;
            buff.putVarInt(len).put(bytes);
        }

        @Override
        public void write(final WriteBuffer buff, final Object[] obj, final int len,
                final boolean key) {
            for (int i = 0; i < len; i++) {
                write(buff, obj[i]);
            }
        }

    }

    private final class ImporterImpl implements Importer {
        @Override
        public void close() {
            H2MVStorage.this.close();
        }

        @Override
        public void createTree(final TreeName treeName) {
            openTree(treeName);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            getTree(treeName).put(key.toByteArray(), value.toByteArray());
        }
    }

    private final class StorageImpl implements WriteableStorage {
        @Override
        public void create(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            getTree(treeName).put(key.toByteArray(), value.toByteArray());
        }

        @Override
        public void delete(final TreeName treeName, final ByteSequence key) {
            getTree(treeName).remove(key);
        }

        @Override
        public ByteString read(final TreeName treeName, final ByteSequence key) {
            return ByteString.wrap(getTree(treeName).get(key.toByteArray()));
        }

        @Override
        public void update(final TreeName treeName, final ByteSequence key, final UpdateFunction f) {
            final MVMap<byte[], byte[]> tree = getTree(treeName);
            final byte[] kb = key.toByteArray();
            final byte[] vb = f.computeNewValue(null).toByteArray();
            final byte[] ovb = tree.putIfAbsent(kb, vb);
            if (ovb != null) {
                final byte[] nvb = f.computeNewValue(ByteString.wrap(ovb)).toByteArray();
                tree.put(kb, nvb);
            }
        }
    }

    private static final ByteArrayDataType BYTE_ARRAY_TYPE = new ByteArrayDataType();
    private static final File DB_DIR = new File("target/h2Backend");
    private static final File DB_FILE = new File(DB_DIR, "db");

    private MVStore store;
    private final Map<TreeName, MVMap<byte[], byte[]>> trees =
            new HashMap<TreeName, MVMap<byte[], byte[]>>();

    @Override
    public void close() {
        if (store != null) {
            store.close();
            store = null;
        }
        trees.clear();
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        // Nothing to do.
    }

    @Override
    public void open() throws Exception {
        store = new MVStore.Builder().fileName(DB_FILE.toString()).cacheSize(256).open();
        trees.clear();
    }

    @Override
    public void openTree(final TreeName treeName) {
        trees.put(treeName, openTree(store, treeName));
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        return operation.run(new StorageImpl());
    }

    @Override
    public Importer startImport() throws Exception {
        clearAndCreateDbDir(DB_DIR);
        open();
        return new ImporterImpl();
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        operation.run(new StorageImpl());
    }

    private MVMap<byte[], byte[]> getTree(final TreeName treeName) {
        return trees.get(treeName);
    }

    private MVMap<byte[], byte[]> openTree(final MVStore store, final TreeName treeName) {
        return store.openMap(treeName.toString(), new MVMap.Builder<byte[], byte[]>().keyType(
                BYTE_ARRAY_TYPE).valueType(BYTE_ARRAY_TYPE));
    }
}
