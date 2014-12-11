package org.opendj.scratch.be;

import static org.forgerock.util.Utils.closeSilently;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DataIO;
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
                return trees.get(name).get(key);
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return trees.get(name).get(key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                // FIXME: how do we support RMW for MVCC? Pass in the value read in call to getRMW?
                trees.get(name).put(key, value);
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
            public void createTree(final TreeName name, final Comparator<ByteSequence> comparator) {
                final ConcurrentNavigableMap<ByteString, ByteString> tree =
                        db.createTreeMap(name.toString()).comparator(comparator).keySerializer(
                                SERIALIZER).valueSerializer(SERIALIZER).make();
                trees.put(name, tree);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                trees.get(name).put(key, value);
            }
        }

        private DB db;
        private final Map<TreeName, ConcurrentNavigableMap<ByteString, ByteString>> trees =
                new HashMap<TreeName, ConcurrentNavigableMap<ByteString, ByteString>>();

        @Override
        public void close() {
            closeSilently(db);
            trees.clear();
        }

        @Override
        public void initialize(final Map<String, String> options) {
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
        public Importer startImport() {
            return new ImporterImpl();
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            updateTransaction.run(new TxnImpl());
        }
    }

    static final Serializer<ByteString> SERIALIZER = new Serializer<ByteString>() {
        @Override
        public ByteString deserialize(final DataInput in, final int available) throws IOException {
            final int size = DataIO.unpackInt(in);
            final byte[] bytes = new byte[size];
            in.readFully(bytes);
            return ByteString.wrap(bytes);
        }

        @Override
        public boolean equals(final ByteString a1, final ByteString a2) {
            return a1.equals(a2);
        }

        @Override
        public int hashCode(final ByteString bytes) {
            return bytes.hashCode();
        }

        @Override
        public boolean isTrusted() {
            return true;
        }

        @Override
        public void serialize(final DataOutput out, final ByteString value) throws IOException {
            DataIO.packInt(out, value.length());
            out.write(value.toByteArray()); // FIXME: extra copy!
        }
    };
}
