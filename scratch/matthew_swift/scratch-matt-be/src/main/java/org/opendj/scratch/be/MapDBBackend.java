package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public final class MapDBBackend implements Backend {

    private static final class ByteStringKeySerializer extends BTreeKeySerializer<ByteString>
            implements Serializable, Comparator<ByteString> {
        private static final long serialVersionUID = 76749424602734923L;

        @Override
        public int compare(final ByteString o1, final ByteString o2) {
            return o1.compareTo(o2);
        }

        @Override
        public Object[] deserialize(final DataInput in, final int start, final int end,
                final int size) throws IOException {
            final Object[] ret = new Object[size];
            byte[] previous = null;
            for (int i = start; i < end; i++) {
                final byte[] b = leadingValuePackRead(in, previous, 0);
                if (b == null) {
                    continue;
                }
                ret[i] = ByteString.wrap(b);
                previous = b;
            }
            return ret;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof ByteStringKeySerializer) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Comparator<ByteString> getComparator() {
            return this;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public void serialize(final DataOutput out, final int start, final int end,
                final Object[] keys) throws IOException {
            byte[] previous = null;
            for (int i = start; i < end; i++) {
                final byte[] b = ((ByteString) keys[i]).toByteArray();
                leadingValuePackWrite(out, b, previous, 0);
                previous = b;
            }
        }

        @Override
        public String toString() {
            return "BYTE_STRING_KEY_SERIALIZER";
        }
    }

    private static final class EntrySerializer implements Serializer<Entry>, Serializable {
        private static final long serialVersionUID = 4606570185008938026L;

        @Override
        public Entry deserialize(final DataInput in, final int available) throws IOException {
            try {
                return decodeEntry(Serializer.BYTE_ARRAY.deserialize(in, available));
            } catch (final ErrorResultException e) {
                throw new ErrorResultIOException(e);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof EntrySerializer) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int fixedSize() {
            return -1;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public void serialize(final DataOutput out, final Entry value) throws IOException {
            Serializer.BYTE_ARRAY.serialize(out, encodeEntry(value));
        }

        @Override
        public String toString() {
            return "ENTRY_SERIALIZER";
        }
    }

    private static final BTreeKeySerializer<ByteString> BYTE_STRING_KEY_SERIALIZER =
            new ByteStringKeySerializer();
    private static final File DB_DIR = new File("mapBackend");
    private static final File DB_FILE = new File(DB_DIR, "db");

    /**
     * FIXME: using an entry serializer causes Entry objects to be cached. These
     * consume alot of memory, so it is better to cache the ASN1 representation
     * instead.
     */
    @SuppressWarnings("unused")
    private static final Serializer<Entry> ENTRY_SERIALIZER = new EntrySerializer();

    private DB db;
    private ConcurrentNavigableMap<ByteString, Long> description2id;
    private ConcurrentNavigableMap<ByteString, Long> dn2id;
    private ConcurrentNavigableMap<Long, byte[]> id2entry;

    @Override
    public void close() {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    @Override
    public void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        clearAndCreateDbDir(DB_DIR);
        db =
                DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().asyncWriteFlushDelay(30000)
                        .commitFileSyncDisable().transactionDisable().closeOnJvmShutdown().make();
        id2entry =
                db.createTreeMap("id2entry").valueSerializer(Serializer.BYTE_ARRAY)
                        .valuesOutsideNodesEnable().makeLongMap();
        dn2id = db.createTreeMap("dn2id").keySerializer(BYTE_STRING_KEY_SERIALIZER).make();
        description2id =
                db.createTreeMap("description2id").keySerializer(BYTE_STRING_KEY_SERIALIZER).make();
        try {
            for (long nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                final Entry entry = entries.readEntry();
                dn2id.put(encodeDn(entry.getName()), nextEntryId);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    description2id.put(encodedDescription, nextEntryId);
                }
                id2entry.put(nextEntryId, encodeEntry(entry));
            }
        } finally {
            close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        final int cacheSize =
                options.containsKey("cacheSize") ? Integer.valueOf(options.get("cacheSize"))
                        : 100000;
        db =
                DBMaker.newFileDB(DB_FILE).mmapFileEnableIfSupported().closeOnJvmShutdown()
                        .cacheLRUEnable().cacheSize(cacheSize).make();
        id2entry = db.getTreeMap("id2entry");
        dn2id = db.getTreeMap("dn2id");
        description2id = db.getTreeMap("description2id");
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws ErrorResultException {
        // FIXME: add transaction support.
        try {
            // Read entry and apply updates.
            final Long entryId = dn2id.get(encodeDn(request.getName()));
            final Entry entry = decodeEntry(id2entry.get(entryId));
            final ByteString oldDescriptionKey = encodeDescription(entry);
            Entries.modifyEntry(entry, request);
            final ByteString newDescriptionKey = encodeDescription(entry);
            // Update description index.
            final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
            if (comparison != 0) {
                description2id.remove(oldDescriptionKey);
                description2id.put(newDescriptionKey, entryId);
            }
            // Update id2entry index.
            id2entry.put(entryId, encodeEntry(entry));
        } catch (final Exception e) {
            throw internalError(e);
        } finally {

        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws ErrorResultException {
        try {
            return decodeEntry(id2entry.get(description2id.get(encodeDescription(description))));
        } catch (final Exception e) {
            throw internalError(e);
        }
    }

    @Override
    public Entry readEntryByDN(final DN name) throws ErrorResultException {
        try {
            return decodeEntry(id2entry.get(dn2id.get(encodeDn(name))));
        } catch (final Exception e) {
            throw internalError(e);
        }
    }

}
