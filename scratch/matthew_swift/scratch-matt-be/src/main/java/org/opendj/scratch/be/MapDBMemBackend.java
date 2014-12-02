package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.*;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Serializer;

public final class MapDBMemBackend implements Backend {
    private final DB db = DBMaker.newMemoryDirectDB().cacheDisable().closeOnJvmShutdown()
            .transactionDisable().make();
    private ConcurrentNavigableMap<byte[], Long> description2id = db
            .createTreeMap("description2id").comparator(Fun.BYTE_ARRAY_COMPARATOR).keySerializer(
                    Serializer.BYTE_ARRAY).make();
    private ConcurrentNavigableMap<byte[], Long> dn2id = db.createTreeMap("dn2id").comparator(
            Fun.BYTE_ARRAY_COMPARATOR).keySerializer(Serializer.BYTE_ARRAY).make();
    private ConcurrentNavigableMap<Long, byte[]> id2entry = db.createTreeMap("id2entry")
            .valueSerializer(Serializer.BYTE_ARRAY).makeLongMap();

    @Override
    public void close() {
        db.close();
    }

    @Override
    public void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        for (long nextEntryId = 0; entries.hasNext(); nextEntryId++) {
            final Entry entry = entries.readEntry();
            dn2id.put(encodeDn(entry.getName()).toByteArray(), nextEntryId);
            final ByteString encodedDescription = encodeDescription(entry);
            if (encodedDescription != null) {
                description2id.put(encodedDescription.toByteArray(), nextEntryId);
            }
            id2entry.put(nextEntryId, encodeEntry(entry));
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        // Nothing to do.
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        try {
            // Read entry and apply updates.
            final Long entryId = dn2id.get(encodeDn(request.getName()).toByteArray());
            final Entry entry = decodeEntry(id2entry.get(entryId));
            final ByteString oldDescriptionKey = encodeDescription(entry);
            Entries.modifyEntry(entry, request);
            final ByteString newDescriptionKey = encodeDescription(entry);
            // Update description index.
            final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
            if (comparison != 0) {
                description2id.remove(oldDescriptionKey.toByteArray());
                description2id.put(newDescriptionKey.toByteArray(), entryId);
            }
            // Update id2entry index.
            id2entry.put(entryId, encodeEntry(entry));
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws LdapException {
        try {
            return decodeEntry(id2entry.get(description2id.get(encodeDescription(description)
                    .toByteArray())));
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

    @Override
    public Entry readEntryByDN(final DN name) throws LdapException {
        try {
            return decodeEntry(id2entry.get(dn2id.get(encodeDn(name).toByteArray())));
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

}
