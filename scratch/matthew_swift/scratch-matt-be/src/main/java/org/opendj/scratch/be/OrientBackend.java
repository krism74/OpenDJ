package org.opendj.scratch.be;

import static org.opendj.scratch.be.Util.createDbDir;
import static org.opendj.scratch.be.Util.internalError;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

import com.orientechnologies.common.serialization.types.OBinaryTypeSerializer;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.ORuntimeKeyIndexDefinition;
import com.orientechnologies.orient.core.intent.OIntentMassiveInsert;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public final class OrientBackend implements Backend {
    private static final class DbHolder {
        private final ODatabaseDocumentTx db;
        private final OIndex<?> description2entry;
        private final OIndex<?> dn2entry;

        public DbHolder(final ODatabaseDocumentTx db) {
            this.db = db;
            this.dn2entry = db.getMetadata().getIndexManager().getIndex("dn2entry");
            this.description2entry =
                    db.getMetadata().getIndexManager().getIndex("description2entry");
        }

    }

    private static final File DB_DIR = new File("orientBackend");
    private static final String DB_URL = "plocal:" + DB_DIR.getAbsolutePath();

    private final Queue<ODatabaseDocumentTx> activeDbConnections =
            new ConcurrentLinkedQueue<ODatabaseDocumentTx>();

    private final DecodeOptions decodeOptions = new DecodeOptions();
    private final AttributeDescription descriptionAD = AttributeDescription.valueOf("description");

    private final ThreadLocal<DbHolder> threadLocalDb = new ThreadLocal<DbHolder>() {
        @Override
        protected DbHolder initialValue() {
            final ODatabaseDocumentTx db = new ODatabaseDocumentTx(DB_URL).open("admin", "admin");
            activeDbConnections.add(db);
            return new DbHolder(db);
        }
    };

    @Override
    public void close() {
        for (final ODatabaseDocumentTx db : activeDbConnections) {
            db.close();
        }
    }

    @Override
    public void importEntries(final EntryReader entries, final Map<String, String> options)
            throws Exception {
        createDbDir(DB_DIR);
        final ODatabaseDocumentTx db = new ODatabaseDocumentTx(DB_URL).create();
        final OIndex<?> dn2entry =
                db.getMetadata().getIndexManager().createIndex("dn2entry", "UNIQUE",
                        new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID), null,
                        null, null);
        final OIndex<?> description2entry =
                db.getMetadata().getIndexManager().createIndex("description2entry", "UNIQUE",
                        new ORuntimeKeyIndexDefinition<byte[]>(OBinaryTypeSerializer.ID), null,
                        null, null);
        db.declareIntent(new OIntentMassiveInsert());
        try {
            while (entries.hasNext()) {
                final Entry entry = entries.readEntry();
                final ORecordBytes id2entry = new ORecordBytes(db, Util.encodeEntry(entry));
                id2entry.save();

                dn2entry.put(encodeDn(entry.getName()), id2entry);
                final ByteString encodedDescription = encodeDescription(entry);
                if (encodedDescription != null) {
                    description2entry.put(encodedDescription.toByteArray(), id2entry);
                }
            }
        } finally {
            db.close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {

    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws ErrorResultException {
        // TODO Auto-generated method stub

    }

    @Override
    public Entry readEntry(final DN name) throws ErrorResultException {
        final DbHolder db = threadLocalDb.get();
        final byte[] key = encodeDn(name);
        final ORecordId id = (ORecordId) db.dn2entry.get(key);
        final ORecordBytes entry = db.db.getRecord(id);
        final ASN1Reader asn1Reader = ASN1.getReader(entry.toStream());
        try {
            return LDAP.readEntry(asn1Reader, decodeOptions);
        } catch (final IOException e) {
            throw internalError(e);
        }
    }

    private ByteString encodeDescription(final Entry entry) throws DecodeException {
        final Attribute descriptionAttribute = entry.getAttribute(descriptionAD);
        if (descriptionAttribute == null) {
            return null;
        }
        final ByteString descriptionValue = descriptionAttribute.firstValue();
        return descriptionAD.getAttributeType().getEqualityMatchingRule().normalizeAttributeValue(
                descriptionValue);
    }

    private byte[] encodeDn(final DN dn) throws ErrorResultException {
        return ByteString.valueOf(dn.toNormalizedString()).toByteArray();
    }

}
