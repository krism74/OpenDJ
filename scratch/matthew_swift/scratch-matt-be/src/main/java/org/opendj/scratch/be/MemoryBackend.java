package org.opendj.scratch.be;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public final class MemoryBackend extends AbstractBackend {

    public static final class MapDBMemStorage implements Storage {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private DB db;
        private final Map<TreeName, ConcurrentNavigableMap<ByteString, ByteString>> trees =
                new HashMap<TreeName, ConcurrentNavigableMap<ByteString, ByteString>>();

        @Override
        public void close() {
            lock.writeLock().lock();
            try {
                if (db != null) {
                    db.close();
                    db = null;
                }
                trees.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void createTree(TreeName name, Comparator<ByteSequence> comparator) {
            lock.writeLock().lock();
            try {
                // FIXME: use correct serializers.
                ConcurrentNavigableMap<ByteString, ByteString> tree =
                        db.createTreeMap(name.toString()).comparator(comparator).keySerializer(
                                Serializer.BYTE_ARRAY).valueSerializer(Serializer.BYTE_ARRAY)
                                .make();
                trees.put(name, tree);
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void deleteTrees(TreeName name) {
            // FIXME: delete subtrees
            lock.writeLock().lock();
            try {
                ConcurrentNavigableMap<ByteString, ByteString> tree = trees.remove(name);
                if (tree != null) {
                    db.delete(name.toString());
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void open(Map<String, String> options) {
            lock.writeLock().lock();
            try {
                if (db != null) {
                    throw new IllegalStateException("Already open!");
                }
                db =
                        DBMaker.newMemoryDirectDB().cacheDisable().closeOnJvmShutdown()
                                .transactionDisable().make();
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void startImport() {
            lock.writeLock().lock();
        }

        @Override
        public void endImport() {
            lock.writeLock().unlock();
        }

        @Override
        public <T> T read(ReadTransaction<T> readTransaction) throws Exception {
            lock.readLock().lock();
            try {
                return readTransaction.run(new MapDBTxn());
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void update(UpdateTransaction updateTransaction) throws Exception {
            lock.readLock().lock();
            try {
                updateTransaction.run(new MapDBTxn());
            } finally {
                lock.readLock().unlock();
            }
        }

        public final class MapDBTxn implements Txn {

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
