package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
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
import org.opendj.scratch.be.spi.Importer;
import org.opendj.scratch.be.spi.ReadOperation;
import org.opendj.scratch.be.spi.ReadableStorage;
import org.opendj.scratch.be.spi.Storage;
import org.opendj.scratch.be.spi.StorageRuntimeException;
import org.opendj.scratch.be.spi.TreeName;
import org.opendj.scratch.be.spi.WriteOperation;
import org.opendj.scratch.be.spi.WriteableStorage;

final class Backend implements Closeable {
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
    public void close() {
        lock.writeLock().lock();
        try {
            storage.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    void importEntries(final EntryReader entries) throws Exception {
        lock.writeLock().lock();
        try {
            final Importer importer = storage.startImport();
            importer.createTree(id2entry);
            importer.createTree(dn2id);
            importer.createTree(description2id);
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

    void initialize(final Map<String, String> options) throws Exception {
        storage.initialize(options);
    }

    void modifyEntry(final ModifyRequest request) throws LdapException {
        lock.readLock().lock();
        try {
            storage.write(new WriteOperation() {
                @Override
                public void run(final WriteableStorage storage) throws Exception {
                    final ByteString dbId = readDn2Id(storage, request.getName());
                    final Entry entry = readId2Entry(storage, dbId);
                    final ByteString oldDescriptionKey = encodeDescription(entry);
                    Entries.modifyEntry(entry, request);
                    final ByteString newDescriptionKey = encodeDescription(entry);
                    final boolean descriptionHasChanged =
                            oldDescriptionKey.compareTo(newDescriptionKey) != 0;
                    if (descriptionHasChanged) {
                        storage.delete(description2id, oldDescriptionKey);
                        storage.create(description2id, newDescriptionKey, dbId);
                    }
                    storage.create(id2entry, dbId, encodeEntry(entry));
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    void open() throws Exception {
        lock.writeLock().lock();
        try {
            storage.open();
            storage.openTree(id2entry);
            storage.openTree(dn2id);
            storage.openTree(description2id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    Entry readEntryByDescription(final ByteString description) throws LdapException {
        lock.readLock().lock();
        try {
            return storage.read(new ReadOperation<Entry>() {
                @Override
                public Entry run(final ReadableStorage storage) throws Exception {
                    return readId2Entry(storage, readDescription2Id(storage, description));
                }
            });
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    Entry readEntryByDN(final DN name) throws LdapException {
        lock.readLock().lock();
        try {
            return storage.read(new ReadOperation<Entry>() {
                @Override
                public Entry run(final ReadableStorage storage) throws Exception {
                    return readId2Entry(storage, readDn2Id(storage, name));
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

    private ByteString readDescription2Id(final ReadableStorage txn, final ByteString description)
            throws LdapException, StorageRuntimeException {
        return checkNoSuchObject(txn.read(description2id, description));
    }

    private ByteString readDn2Id(final ReadableStorage txn, final DN name) throws LdapException,
            StorageRuntimeException {
        return checkNoSuchObject(txn.read(dn2id, name.toIrreversibleNormalizedByteString()));
    }

    private Entry readId2Entry(final ReadableStorage txn, final ByteString dbId)
            throws LdapException, StorageRuntimeException {
        return decodeEntry(checkNoSuchObject(txn.read(id2entry, dbId)));
    }

}
