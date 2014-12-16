package org.opendj.scratch.be.impl;

import static org.forgerock.util.Utils.closeSilently;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.UpdateFunction;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

@SuppressWarnings("javadoc")
public final class MapDbMemStorage implements Storage {
    private final class StorageImpl implements WriteableStorage {
        @Override
        public void create(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(key.toByteArray(), value.toByteArray());
        }

        @Override
        public ByteString read(final TreeName treeName, final ByteSequence key) {
            return ByteString.wrap(trees.get(treeName).get(key.toByteArray()));
        }

        @Override
        public void update(TreeName treeName, ByteSequence key, UpdateFunction f) {
            final ConcurrentNavigableMap<byte[], byte[]> tree = trees.get(treeName);
            final byte[] kb = key.toByteArray();
            for (;;) {
                final byte[] vb = f.computeNewValue(null).toByteArray();
                final byte[] ovb = tree.putIfAbsent(kb, vb);
                if (ovb == null) {
                    return;
                }
                final byte[] nvb = f.computeNewValue(ByteString.wrap(ovb)).toByteArray();
                if (tree.replace(kb, ovb, nvb)) {
                    return;
                }
                // CONFLICT.
            }
        }

        @Override
        public void delete(final TreeName treeName, final ByteSequence key) {
            trees.get(treeName).remove(key);
        }
    }

    private final class ImporterImpl implements Importer {
        @Override
        public void close() {
            // Nothing to do.
        }

        @Override
        public void createTree(final TreeName treeName) {
            final ConcurrentNavigableMap<byte[], byte[]> tree =
                    db.createTreeMap(treeName.toString()).keySerializer(
                            BTreeKeySerializer.BYTE_ARRAY).valueSerializer(Serializer.BYTE_ARRAY)
                            .make();
            trees.put(treeName, tree);
        }

        @Override
        public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value) {
            trees.get(treeName).put(key.toByteArray(), value.toByteArray());
        }
    }

    private DB db;
    private final Map<TreeName, ConcurrentNavigableMap<byte[], byte[]>> trees =
            new HashMap<TreeName, ConcurrentNavigableMap<byte[], byte[]>>();

    @Override
    public void initialize(final Map<String, String> options) {
        // Nothing to do.
    }

    @Override
    public Importer startImport() {
        db =
                DBMaker.newMemoryDirectDB().cacheDisable().closeOnJvmShutdown()
                        .transactionDisable().make();
        return new ImporterImpl();
    }

    @Override
    public void open() {
        // No op - DB created during import.
    }

    @Override
    public void openTree(final TreeName treeName) {
        // The trees should already be present after the import.
        if (!trees.containsKey(treeName)) {
            throw new IllegalStateException();
        }
    }

    @Override
    public <T> T read(final ReadOperation<T> operation) throws Exception {
        return operation.run(new StorageImpl());
    }

    @Override
    public void write(final WriteOperation operation) throws Exception {
        operation.run(new StorageImpl());
    }

    @Override
    public void close() {
        closeSilently(db);
        trees.clear();
    }
}
