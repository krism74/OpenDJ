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

public final class MapDBBackend implements Backend {
    private static final File DB_DIR = new File("target/mapBackend");
    private static final File DB_FILE = new File(DB_DIR, "db");

    private DB db;
    private ConcurrentNavigableMap<byte[], Long> description2id;
    private ConcurrentNavigableMap<byte[], Long> dn2id;
    private ConcurrentNavigableMap<Long, byte[]> id2entry;
    private Map<String, String> options;

    @Override
    public void close() {
        if (db != null) {
            db.commit();
            // db.compact();
            db.close();
            db = null;
        }
    }

    @Override
    public void importEntries(final EntryReader entries) throws Exception {
        clearAndCreateDbDir(DB_DIR);
        DB db =
                DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().asyncWriteEnable()
                        .commitFileSyncDisable().transactionDisable().closeOnJvmShutdown().make();
        ConcurrentNavigableMap<Long, byte[]> id2entry;
        if (options.containsKey("valuesOutsideNodes")) {
            id2entry =
                    db.createTreeMap("id2entry").valueSerializer(Serializer.BYTE_ARRAY)
                            .valuesOutsideNodesEnable().makeLongMap();
        } else {
            id2entry =
                    db.createTreeMap("id2entry").valueSerializer(Serializer.BYTE_ARRAY)
                            .makeLongMap();
        }
        ConcurrentNavigableMap<byte[], Long> dn2id =
                db.createTreeMap("dn2id").comparator(Fun.BYTE_ARRAY_COMPARATOR).keySerializer(
                        Serializer.BYTE_ARRAY).make();
        ConcurrentNavigableMap<byte[], Long> description2id =
                db.createTreeMap("description2id").comparator(Fun.BYTE_ARRAY_COMPARATOR)
                        .keySerializer(Serializer.BYTE_ARRAY).make();
        try {
            for (long nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                final Entry entry = entries.readEntry();
                dn2id.put(encodeDn(entry.getName()).toByteArray(), nextEntryId);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    description2id.put(encodedDescription.toByteArray(), nextEntryId);
                }
                id2entry.put(nextEntryId, encodeEntry(entry));
            }
        } finally {
            db.close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        this.options = options;
        final boolean useCache = options.containsKey("useCache");
        final int cacheSize =
                options.containsKey("cacheSize") ? Integer.valueOf(options.get("cacheSize"))
                        : 32768;
        if (useCache) {
            db =
                    DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().closeOnJvmShutdown()
                            .cacheSize(cacheSize).commitFileSyncDisable().make();
        } else {
            db =
                    DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().closeOnJvmShutdown()
                            .cacheDisable().commitFileSyncDisable().make();
        }
        id2entry = db.getTreeMap("id2entry");
        dn2id = db.getTreeMap("dn2id");
        description2id = db.getTreeMap("description2id");
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        // FIXME: add transaction support.
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
            db.commit();
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
