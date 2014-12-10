package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.clearAndCreateDbDir;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

@SuppressWarnings("javadoc")
public final class RocksDBBackend extends AbstractBackend {
    public RocksDBBackend() {
        super(new StorageImpl());
    }

    private static final class StorageImpl implements Storage {
        private final class ImporterImpl implements Importer {
            @Override
            public void createTree(final TreeName name, final Comparator<ByteSequence> comparator) {
                nameToPrefix.put(name, nextPrefix++);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                try {
                    db.put(prefixKey(name, key), value.toByteArray());
                } catch (final RocksDBException e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public void close() {
                StorageImpl.this.close();
            }
        }

        private final class TxnImpl implements UpdateTxn {
            private final WriteBatch batchUpdate;

            private TxnImpl(final WriteBatch batchUpdate) {
                this.batchUpdate = batchUpdate;
            }

            @Override
            public ByteString get(final TreeName name, final ByteString key) {
                try {
                    return wrap(db.get(prefixKey(name, key)));
                } catch (final RocksDBException e) {
                    throw new StorageRuntimeException(e);
                }
            }

            @Override
            public ByteString getRMW(final TreeName name, final ByteString key) {
                return get(name, key);
            }

            @Override
            public void put(final TreeName name, final ByteString key, final ByteString value) {
                batchUpdate.put(prefixKey(name, key), value.toByteArray());
            }

            @Override
            public void remove(final TreeName name, final ByteString key) {
                batchUpdate.remove(prefixKey(name, key));
            }
        }

        private static final File DB_DIR = new File("target/rocksBackend");
        private RocksDB db;
        private Options dbOptions;

        /*
         * In practice we would support a large number of indexes as well as
         * persisting the prefix mapping in the DB.
         */
        private byte nextPrefix = 0;
        private final Map<TreeName, Byte> nameToPrefix = new HashMap<TreeName, Byte>();

        @Override
        public void initialize(final Map<String, String> options) throws Exception {
            RocksDB.loadLibrary();
            dbOptions = new Options().setCreateIfMissing(true);
        }

        @Override
        public Importer startImport() throws Exception {
            clearAndCreateDbDir(DB_DIR);
            open();
            return new ImporterImpl();
        }

        @Override
        public void open() throws Exception {
            db = RocksDB.open(dbOptions, DB_DIR.toString());
        }

        @Override
        public void openTree(final TreeName name, final Comparator<ByteSequence> comparator) {
            if (!nameToPrefix.containsKey(name)) {
                throw new IllegalStateException();
            }
        }

        @Override
        public <T> T read(final ReadTransaction<T> readTransaction) throws Exception {
            return readTransaction.run(new TxnImpl(null));
        }

        @Override
        public void update(final UpdateTransaction updateTransaction) throws Exception {
            final WriteBatch batchUpdate = new WriteBatch();
            final WriteOptions writeOptions = new WriteOptions();
            try {
                updateTransaction.run(new TxnImpl(batchUpdate));
                db.write(writeOptions, batchUpdate);
            } finally {
                writeOptions.dispose();
                batchUpdate.dispose();
            }
        }

        @Override
        public void close() {
            if (db != null) {
                db.close();
                db = null;
                dbOptions.dispose();
                dbOptions = null;
            }
        }

        private byte[] prefixKey(final TreeName name, final ByteString key) {
            final ByteStringBuilder buffer = new ByteStringBuilder(key.length() + 1);
            buffer.append(nameToPrefix.get(name));
            buffer.append(key);
            return buffer.toByteArray();
        }

        private ByteString wrap(final byte[] value) {
            return value != null ? ByteString.wrap(value) : null;
        }
    }

}
