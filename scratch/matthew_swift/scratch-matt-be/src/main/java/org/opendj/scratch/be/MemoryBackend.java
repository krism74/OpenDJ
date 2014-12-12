package org.opendj.scratch.be;

import static org.forgerock.util.Utils.closeSilently;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

@SuppressWarnings("javadoc")
public final class MemoryBackend extends Backend {

    public MemoryBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private final class TxnImpl implements UpdateTxn {
            @Override
            public ByteString get(final TreeName name, final ByteString key) {
                return ByteString.wrap(trees.get(name).get(key.toByteArray()));
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return get(name, key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                // FIXME: how do we support RMW for MVCC? Pass in the value read in call to getRMW?
                trees.get(name).put(key.toByteArray(), value.toByteArray());
            }

            @Override
            public boolean remove(final TreeName name, final ByteString key) {
                return trees.get(name).remove(key) != null;
            }
        }

        private final class ImporterImpl implements Importer {
            @Override
            public void close() {
                // Nothing to do.
            }

            @Override
            public void createTree(final TreeName name) {
                final ConcurrentNavigableMap<byte[], byte[]> tree =
                        db.createTreeMap(name.toString()).keySerializer(
                                BTreeKeySerializer.BYTE_ARRAY).valueSerializer(
                                Serializer.BYTE_ARRAY).make();
                trees.put(name, tree);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                trees.get(name).put(key.toByteArray(), value.toByteArray());
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
        public void openTree(final TreeName name, final Comparator<ByteSequence> comparator) {
            // The trees should already be present after the import.
            if (!trees.containsKey(name)) {
                throw new IllegalStateException();
            }
        }

        @Override
        public <T> T read(final ReadTransaction<T> readTransaction) throws Exception {
            return readTransaction.run(new TxnImpl());
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            updateTransaction.run(new TxnImpl());
        }

        @Override
        public void close() {
            closeSilently(db);
            trees.clear();
        }

    }
}
