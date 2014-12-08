package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.decodeEntry;
import static org.opendj.scratch.be.Util.encodeDescription;
import static org.opendj.scratch.be.Util.encodeEntry;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

public abstract class AbstractBackend implements Backend {

    interface Storage extends Closeable {

        @Override
        void close();

        void createTree(TreeName tree, Comparator<ByteSequence> comparator);

        void deleteTrees(TreeName tree);

        void endImport();

        void open(Map<String, String> options);

        <T> T read(ReadTransaction<T> readTransaction);

        void startImport();

        void update(UpdateTransaction updateTransaction);
    }

    @SuppressWarnings("serial")
    final class StorageRuntimeException extends RuntimeException {

        public StorageRuntimeException(String message) {
            super(message);
        }

        public StorageRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }

        public StorageRuntimeException(Throwable cause) {
            super(cause);
        }
    }

    static final class TreeName {
        public static TreeName of(final String... names) {
            return new TreeName(Arrays.asList(names));
        }

        private final List<String> names;

        public TreeName(final List<String> names) {
            this.names = names;
        }

        public TreeName child(final String name) {
            final List<String> newNames = new ArrayList<String>(names.size() + 1);
            newNames.addAll(names);
            newNames.add(name);
            return new TreeName(newNames);
        }

        public List<String> getNames() {
            return names;
        }
    }

    interface Txn {

        ByteString get(TreeName tree, ByteString key);

        ByteString getRMW(TreeName tree, ByteString key);

        void put(TreeName tree, ByteString key, ByteString value);

        void remove(TreeName tree, ByteString key);

        // TODO: cursoring, contains, etc.

    }

    interface ReadTransaction<T> {
        T run(Txn txn) throws Exception;
    }

    interface UpdateTransaction {
        void run(Txn txn) throws Exception;
    }

    private final TreeName suffix = TreeName.of("dc=example,dc=com");
    private final TreeName description2id = suffix.child("description2id");
    private final TreeName dn2id = suffix.child("dn2id");
    private final TreeName id2entry = suffix.child("id2entry");

    private final Storage storage;

    AbstractBackend(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public final void close() {
        storage.close();
    }

    @Override
    public final void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        storage.deleteTrees(suffix);
        storage.createTree(id2entry, ByteSequence.COMPARATOR);
        storage.createTree(dn2id, ByteSequence.COMPARATOR);
        storage.createTree(description2id, ByteSequence.COMPARATOR);

        storage.startImport();
        try {
            storage.update(new UpdateTransaction() {
                @Override
                public void run(final Txn txn) throws Exception {
                    for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                        final Entry entry = entries.readEntry();
                        final ByteString dbId = ByteString.valueOf(nextEntryId);
                        txn.put(id2entry, dbId, ByteString.wrap(encodeEntry(entry)));
                        txn.put(dn2id, dbId, entry.getName().toIrreversibleNormalizedByteString());
                        final ByteString encodedDescription = encodeDescription(entry);
                        if (encodedDescription != null) {
                            txn.put(description2id, encodedDescription, dbId);
                        }
                    }
                }
            });
        } finally {
            storage.endImport();
        }
    }

    @Override
    public final void initialize(final Map<String, String> options) throws Exception {
        storage.open(options);
    }

    @Override
    public final void modifyEntry(final ModifyRequest request) throws LdapException {
        storage.update(new UpdateTransaction() {
            @Override
            public void run(final Txn txn) throws Exception {
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
    }

    @Override
    public final Entry readEntryByDescription(final ByteString description) throws LdapException {
        return storage.read(new ReadTransaction<Entry>() {
            @Override
            public Entry run(final Txn txn) throws Exception {
                final ByteString dbId = readDescription2Id(txn, description);
                return readId2Entry(txn, dbId, true);
            }
        });
    }

    @Override
    public final Entry readEntryByDN(final DN name) throws LdapException {
        return storage.read(new ReadTransaction<Entry>() {
            @Override
            public Entry run(final Txn txn) throws Exception {
                final ByteString dbId = readDn2Id(txn, name);
                return readId2Entry(txn, dbId, true);
            }
        });
    }

    private ByteString readDescription2Id(final Txn txn, final ByteString description)
            throws StorageRuntimeException {
        return txn.get(description2id, description);
    }

    private ByteString readDn2Id(final Txn txn, final DN name) throws StorageRuntimeException {
        return txn.get(dn2id, name.toIrreversibleNormalizedByteString());
    }

    private Entry readId2Entry(final Txn txn, final ByteString dbId, final boolean isRMW)
            throws LdapException, StorageRuntimeException {
        final ByteString entry = isRMW ? txn.getRMW(id2entry, dbId) : txn.get(id2entry, dbId);
        return decodeEntry(entry.toByteArray());
    }

}
