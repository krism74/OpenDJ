package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.*;

import java.io.File;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

public final class RocksDBBackend implements Backend {
    private static final File DB_DIR = new File("target/rocksBackend");
    private static final byte PREFIX_DESCRIPTION2ID = 2;
    private static final byte PREFIX_DN2ID = 1;
    private static final byte PREFIX_ID2ENTRY = 0;

    private RocksDB db;
    private Options dbOptions;

    @Override
    public void close() {
        if (db != null) {
            db.close();
            db = null;
            dbOptions.dispose();
            dbOptions = null;
        }
    }

    @Override
    public void importEntries(final EntryReader entries) throws Exception {
        clearAndCreateDbDir(DB_DIR);

        RocksDB.loadLibrary();
        dbOptions = new Options().setCreateIfMissing(true);
        db = RocksDB.open(dbOptions, DB_DIR.toString());

        try {
            final ByteStringBuilder builder = new ByteStringBuilder();
            for (int nextEntryId = 0; entries.hasNext(); nextEntryId++) {
                builder.clear();
                builder.append(PREFIX_ID2ENTRY);
                builder.append(nextEntryId);
                final byte[] id = builder.toByteArray();

                final Entry entry = entries.readEntry();
                builder.clear();
                builder.append(encodeEntry(entry));
                db.put(id, builder.toByteArray());

                builder.clear();
                builder.append(PREFIX_DN2ID);
                builder.append(encodeDn(entry.getName()));
                db.put(builder.toByteArray(), id);

                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    builder.clear();
                    builder.append(PREFIX_DESCRIPTION2ID);
                    builder.append(encodedDescription);
                    db.put(builder.toByteArray(), id);
                }
            }
        } finally {
            close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        RocksDB.loadLibrary();
        dbOptions = new Options().setCreateIfMissing(true);
        db = RocksDB.open(dbOptions, DB_DIR.toString());
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws LdapException {
        final WriteBatch batchUpdate = new WriteBatch();
        final WriteOptions writeOptions = new WriteOptions();
        try {
            // Read entry and apply updates.
            final byte[] entryId = db.get(encodeDnKey(request.getName()));
            final Entry entry = decodeEntry(db.get(entryId));
            final ByteString oldDescriptionKey = encodeDescription(entry);
            Entries.modifyEntry(entry, request);
            final ByteString newDescriptionKey = encodeDescription(entry);
            // Update description index.
            final int comparison = oldDescriptionKey.compareTo(newDescriptionKey);
            if (comparison != 0) {
                final byte[] oldKey = toDescriptionKey(oldDescriptionKey);
                final byte[] newKey = toDescriptionKey(newDescriptionKey);
                if (comparison < 0) {
                    batchUpdate.remove(oldKey);
                    batchUpdate.put(newKey, entryId);
                } else {
                    batchUpdate.put(newKey, entryId);
                    batchUpdate.remove(oldKey);
                }
            }
            // Update id2entry index.
            batchUpdate.put(entryId, encodeEntry(entry));
            db.write(writeOptions, batchUpdate);
        } catch (final Exception e) {
            throw adaptException(e);
        } finally {
            writeOptions.dispose();
            batchUpdate.dispose();
        }
    }

    @Override
    public Entry readEntryByDescription(final ByteString description) throws LdapException {
        try {
            return decodeEntry(db.get(db.get(toDescriptionKey(encodeDescription(description)))));
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

    @Override
    public Entry readEntryByDN(final DN name) throws LdapException {
        try {
            return decodeEntry(db.get(db.get(encodeDnKey(name))));
        } catch (final Exception e) {
            throw adaptException(e);
        }
    }

    private byte[] encodeDnKey(final DN name) {
        return toDnKey(encodeDn(name));
    }

    private byte[] toDescriptionKey(final ByteString encodedDescription) {
        final byte[] key = new byte[encodedDescription.length() + 1];
        key[0] = PREFIX_DESCRIPTION2ID;
        encodedDescription.copyTo(key, 1);
        return key;
    }

    private byte[] toDnKey(final ByteString encodedDn) {
        final byte[] key = new byte[encodedDn.length() + 1];
        key[0] = PREFIX_DN2ID;
        encodedDn.copyTo(key, 1);
        return key;
    }

}
