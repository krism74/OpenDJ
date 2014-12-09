package org.opendj.scratch.be;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

import static org.opendj.scratch.be.Util.*;

@SuppressWarnings("javadoc")
public abstract class AbstractBackend implements Backend {

    interface Importer extends Closeable {
        void createTree(TreeName name, Comparator<ByteSequence> comparator);

        void put(TreeName name, ByteString key, ByteString value);

        @Override
        void close();
    }

    interface ReadTransaction<T> {
        T run(ReadTxn txn) throws Exception;
    }

    interface ReadTxn {
        ByteString get(TreeName name, ByteString key);

        ByteString getRMW(TreeName name, ByteString key);

        // TODO: cursoring, contains, etc.
    }

    interface Storage extends Closeable {
        void initialize(Map<String, String> options) throws Exception;

        Importer startImport() throws Exception;

        void open() throws Exception;

        // FIXME: does this need to pass in the comparator?
        void openTree(TreeName name, Comparator<ByteSequence> comparator);

        <T> T read(ReadTransaction<T> readTransaction) throws Exception;

        void update(UpdateTransaction updateTransaction) throws Exception;

        @Override
        void close();
    }

    @SuppressWarnings("serial")
    static final class StorageRuntimeException extends RuntimeException {

        public StorageRuntimeException(final String message) {
            super(message);
        }

        public StorageRuntimeException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public StorageRuntimeException(final Throwable cause) {
            super(cause);
        }
    }

    /** Assumes name components don't contain a '/'. */
    static final class TreeName {
        public static TreeName of(final String... names) {
            return new TreeName(Arrays.asList(names));
        }

        private final List<String> names;
        private final String s;

        public TreeName(final List<String> names) {
            this.names = names;
            final StringBuilder builder = new StringBuilder();
            for (final String name : names) {
                builder.append('/');
                builder.append(name);
            }
            this.s = builder.toString();
        }

        public List<String> getNames() {
            return names;
        }

        public TreeName child(final String name) {
            final List<String> newNames = new ArrayList<String>(names.size() + 1);
            newNames.addAll(names);
            newNames.add(name);
            return new TreeName(newNames);
        }

        public TreeName getSuffix() {
            if (names.size() == 0) {
                throw new IllegalStateException();
            }
            return new TreeName(Collections.singletonList(names.get(0)));
        }

        public boolean isSuffixOf(TreeName tree) {
            if (names.size() > tree.names.size()) {
                return false;
            }
            for (int i = 0; i < names.size(); i++) {
                if (!tree.names.get(i).equals(names.get(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof TreeName) {
                return s.equals(((TreeName) obj).s);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return s.hashCode();
        }

        @Override
        public String toString() {
            return s;
        }
    }

    interface UpdateTransaction {
        void run(UpdateTxn txn) throws Exception;
    }

    interface UpdateTxn extends ReadTxn {
        void put(TreeName name, ByteString key, ByteString value);

        void remove(TreeName name, ByteString key);
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final TreeName suffix = TreeName.of("dc=example,dc=com");
    private final TreeName description2id = suffix.child("description2id");
    private final TreeName dn2id = suffix.child("dn2id");
    private final TreeName id2entry = suffix.child("id2entry");
    private final Storage storage;

    AbstractBackend(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public void initialize(Map<String, String> options) throws Exception {
        storage.initialize(options);
    }

    @Override
    public final void close() {
        lock.writeLock().lock();
        try {
            storage.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public final void importEntries(final EntryReader entries) throws Exception {
        lock.writeLock().lock();
        try {
            final Importer importer = storage.startImport();
            importer.createTree(id2entry, ByteSequence.COMPARATOR);
            importer.createTree(dn2id, ByteSequence.COMPARATOR);
            importer.createTree(description2id, ByteSequence.COMPARATOR);
            try {
                for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                    final Entry entry = entries.readEntry();
                    final ByteString dbId = ByteString.valueOf(nextEntryId);
                    importer.put(id2entry, dbId, ByteString.wrap(encodeEntry(entry)));
                    importer.put(dn2id, dbId, entry.getName().toIrreversibleNormalizedByteString());
                    final ByteString encodedDescription = encodeDescription(entry);
                    if (encodedDescription != null) {
                        importer.put(description2id, encodedDescription, dbId);
                    }
                }
            } finally {
                importer.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public final void open() throws Exception {
        lock.writeLock().lock();
        try {
            storage.open();
            storage.openTree(id2entry, ByteSequence.COMPARATOR);
            storage.openTree(dn2id, ByteSequence.COMPARATOR);
            storage.openTree(description2id, ByteSequence.COMPARATOR);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public final void modifyEntry(final ModifyRequest request) throws LdapException {
        lock.readLock().lock();
        try {
            storage.update(new UpdateTransaction() {
                @Override
                public void run(final UpdateTxn txn) throws Exception {
                    final ByteString dbId = readDn2Id(txn, request.getName());
                    final Entry entry = readId2Entry(txn, dbId, true);
                    final ByteString oldDescriptionKey = encodeDescription(entry);
                    Entries.modifyEntry(entry, request);
                    final ByteString newDescriptionKey = encodeDescription(entry);
                    final boolean descriptionHasChanged =
                            oldDescriptionKey.compareTo(newDescriptionKey) != 0;
                    if (descriptionHasChanged) {
                        txn.remove(description2id, oldDescriptionKey);
                        txn.put(description2id, newDescriptionKey, dbId);
                    }
                    txn.put(id2entry, dbId, ByteString.wrap(encodeEntry(entry)));
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public final Entry readEntryByDescription(final ByteString description) throws LdapException {
        lock.readLock().lock();
        try {
            return storage.read(new ReadTransaction<Entry>() {
                @Override
                public Entry run(final ReadTxn txn) throws Exception {
                    final ByteString dbId = readDescription2Id(txn, description);
                    return readId2Entry(txn, dbId, true);
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public final Entry readEntryByDN(final DN name) throws LdapException {
        lock.readLock().lock();
        try {
            return storage.read(new ReadTransaction<Entry>() {
                @Override
                public Entry run(final ReadTxn txn) throws Exception {
                    final ByteString dbId = readDn2Id(txn, name);
                    return readId2Entry(txn, dbId, true);
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private ByteString readDescription2Id(final ReadTxn txn, final ByteString description)
            throws StorageRuntimeException {
        return txn.get(description2id, description);
    }

    private ByteString readDn2Id(final ReadTxn txn, final DN name) throws StorageRuntimeException {
        return txn.get(dn2id, name.toIrreversibleNormalizedByteString());
    }

    private Entry readId2Entry(final ReadTxn txn, final ByteString dbId, final boolean isRMW)
            throws LdapException, StorageRuntimeException {
        final ByteString entry = isRMW ? txn.getRMW(id2entry, dbId) : txn.get(id2entry, dbId);
        return decodeEntry(entry.toByteArray());
    }

}
