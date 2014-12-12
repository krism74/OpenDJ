package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

@SuppressWarnings("javadoc")
public abstract class Backend implements Closeable {
    interface Importer extends Closeable {
        @Override
        void close();

        void createTree(TreeName name, Comparator<ByteSequence> comparator);

        void put(TreeName name, ByteString key, ByteString value);
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
        @Override
        void close();

        void initialize(Map<String, String> options) throws Exception;

        void open() throws Exception;

        // FIXME: does this need to pass in the comparator?
        void openTree(TreeName name, Comparator<ByteSequence> comparator);

        <T> T read(ReadTransaction<T> readTransaction) throws Exception;

        Importer startImport() throws Exception;

        void update(UpdateTransaction updateTransaction) throws Exception;
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

        public TreeName child(final String name) {
            final List<String> newNames = new ArrayList<String>(names.size() + 1);
            newNames.addAll(names);
            newNames.add(name);
            return new TreeName(newNames);
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

        public List<String> getNames() {
            return names;
        }

        public TreeName getSuffix() {
            if (names.size() == 0) {
                throw new IllegalStateException();
            }
            return new TreeName(Collections.singletonList(names.get(0)));
        }

        @Override
        public int hashCode() {
            return s.hashCode();
        }

        public boolean isSuffixOf(final TreeName tree) {
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
        public String toString() {
            return s;
        }
    }

    interface UpdateTransaction {
        void run(UpdateTxn txn) throws Exception;
    }

    interface UpdateTxn extends ReadTxn {
        void put(TreeName name, ByteString key, ByteString value);

        // FIXME implement
        // boolean putIfAbsent(TreeName treeName, ByteString key, ByteString value);

        boolean remove(TreeName name, ByteString key);

        // FIXME implement
        // boolean remove(TreeName name, ByteString key, ByteString value);
    }

    private static final class WriteBuffer {
        private final ASN1Writer asn1Writer;
        private final ByteStringBuilder builder;

        private WriteBuffer() {
            builder = new ByteStringBuilder();
            asn1Writer = ASN1.getWriter(builder, 2048);
        }
    }

    private static final AttributeDescription AD_DESCRIPTION = AttributeDescription
            .valueOf("description");
    private static final DecodeOptions DECODE_OPTIONS = new DecodeOptions();
    private static final ThreadLocal<WriteBuffer> threadLocalBuffer =
            new ThreadLocal<WriteBuffer>() {
                @Override
                protected WriteBuffer initialValue() {
                    return new WriteBuffer();
                };
            };

    static LdapException adaptException(final Exception e) {
        if (e instanceof LdapException) {
            return (LdapException) e;
        }
        return newLdapException(ResultCode.OTHER, e);
    }

    static void clearAndCreateDbDir(final File dbDir) {
        if (dbDir.exists()) {
            for (final File child : dbDir.listFiles()) {
                child.delete();
            }
        } else {
            dbDir.mkdirs();
        }
    }

    static Entry decodeEntry(final ByteString data) throws LdapException {
        final ASN1Reader asn1Reader = ASN1.getReader(data);
        try {
            return LDAP.readEntry(asn1Reader, DECODE_OPTIONS);
        } catch (final IOException e) {
            throw adaptException(e);
        }
    }

    static ByteString encodeDescription(final Entry entry) throws DecodeException {
        final Attribute descriptionAttribute = entry.getAttribute(AD_DESCRIPTION);
        if (descriptionAttribute != null) {
            return AD_DESCRIPTION.getAttributeType().getEqualityMatchingRule()
                    .normalizeAttributeValue(descriptionAttribute.firstValue());
        }
        return null;
    }

    static ByteString encodeEntry(final Entry entry) throws IOException {
        final WriteBuffer buffer = threadLocalBuffer.get();
        buffer.builder.clearAndTruncate(2048, 2048);
        LDAP.writeEntry(buffer.asn1Writer, entry);
        return buffer.builder.toByteString();
    }

    private final TreeName suffix = TreeName.of("dc=example,dc=com");
    private final TreeName id2entry = suffix.child("id2entry");
    private final TreeName dn2id = suffix.child("dn2id");
    private final TreeName description2id = suffix.child("description2id");
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Storage storage;

    Backend(final Storage storage) {
        this.storage = storage;
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
                    importer.put(id2entry, dbId, encodeEntry(entry));
                    importer.put(dn2id, entry.getName().toIrreversibleNormalizedByteString(), dbId);
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

    public void initialize(final Map<String, String> options) throws Exception {
        storage.initialize(options);
    }

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
                    txn.put(id2entry, dbId, encodeEntry(entry));
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

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

    private ByteString checkNoSuchObject(final ByteString value) throws LdapException {
        if (value != null) {
            return value;
        }
        throw LdapException.newLdapException(ResultCode.NO_SUCH_OBJECT);
    }

    private ByteString readDescription2Id(final ReadTxn txn, final ByteString description)
            throws LdapException, StorageRuntimeException {
        return checkNoSuchObject(txn.get(description2id, description));
    }

    private ByteString readDn2Id(final ReadTxn txn, final DN name) throws LdapException,
            StorageRuntimeException {
        return checkNoSuchObject(txn.get(dn2id, name.toIrreversibleNormalizedByteString()));
    }

    private Entry readId2Entry(final ReadTxn txn, final ByteString dbId, final boolean isRMW)
            throws LdapException, StorageRuntimeException {
        final ByteString entry = isRMW ? txn.getRMW(id2entry, dbId) : txn.get(id2entry, dbId);
        return decodeEntry(checkNoSuchObject(entry));
    }

}
