package org.opendj.scratch.be;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public final class MemoryBackend extends AbstractBackend {

    public static final class MapDBMemStorage implements Storage {
        private DB db;
        private final Map<TreeName, ConcurrentNavigableMap<ByteString, ByteString>> trees =
                new HashMap<TreeName, ConcurrentNavigableMap<ByteString, ByteString>>();

        @Override
        public void close() {
            if (db != null) {
                db.close();
                db = null;
            }
            trees.clear();
        }

        @Override
        public void createTree(TreeName name, Comparator<ByteSequence> comparator) {
            // FIXME: use correct serializers.
            ConcurrentNavigableMap<ByteString, ByteString> tree =
                    db.createTreeMap(name.toString()).comparator(comparator).keySerializer(
                            Serializer.BYTE_ARRAY).valueSerializer(Serializer.BYTE_ARRAY).make();
            trees.put(name, tree);
        }

        @Override
        public void deleteTrees(TreeName name) {
            // FIXME: delete subtrees
            ConcurrentNavigableMap<ByteString, ByteString> tree = trees.remove(name);
            if (tree != null) {
                db.delete(name.toString());
            }
        }

        @Override
        public void initialize(Map<String, String> options) {
            // Nothing to do.
        }

        @Override
        public void open() {
            if (db != null) {
                throw new IllegalStateException("Already open!");
            }
            db =
                    DBMaker.newMemoryDirectDB().cacheDisable().closeOnJvmShutdown()
                            .transactionDisable().make();
        }

        @Override
        public Importer startImport() {
            return new Importer() {
                @Override
                public void put(TreeName name, ByteString key, ByteString value) {
                    trees.get(name).put(key, value);
                }

                @Override
                public void close() {
                    // Nothing to do.
                }
            };
        }

        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            return readTransaction.run(new MapDBTxn());
        }

        @Override
        public void update(UpdateTransaction updateTransaction) throws Exception {
            updateTransaction.run(new MapDBTxn());
        }

        public final class MapDBTxn implements UpdateTxn {

            @Override
            public ByteString get(TreeName name, ByteString key) {
                return trees.get(name).get(key);
            }

            @Override
            public ByteString getRMW(TreeName name, ByteString key) {
                return trees.get(name).get(key);
            }

            @Override
            public void put(TreeName name, ByteString key, ByteString value) {
                // FIXME: how do we support RMW for MVCC? Pass in the value read in call to getRMW?
                trees.get(name).put(key, value);
            }

            @Override
            public void remove(TreeName name, ByteString key) {
                trees.get(name).remove(key);
            }
        }
    }

    public MemoryBackend() {
        super(new MapDBMemStorage());
    }
}
