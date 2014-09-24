package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.LdapException.newErrorResult;

import java.io.File;
import java.io.IOException;

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
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;

final class Util {
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

    static void clearAndCreateDbDir(final File dbDir) {
        if (dbDir.exists()) {
            for (final File child : dbDir.listFiles()) {
                child.delete();
            }
        } else {
            dbDir.mkdirs();
        }
    }

    static Entry decodeEntry(final byte[] data) throws LdapException {
        final ASN1Reader asn1Reader = ASN1.getReader(data);
        try {
            return LDAP.readEntry(asn1Reader, DECODE_OPTIONS);
        } catch (final IOException e) {
            throw internalError(e);
        }
    }

    static ByteString encodeDescription(final ByteString description) throws DecodeException {
        return AD_DESCRIPTION.getAttributeType().getEqualityMatchingRule().normalizeAttributeValue(
                description);
    }

    static ByteString encodeDescription(final Entry entry) throws DecodeException {
        final Attribute descriptionAttribute = entry.getAttribute(AD_DESCRIPTION);
        if (descriptionAttribute == null) {
            return null;
        }
        return encodeDescription(descriptionAttribute.firstValue());
    }

    static ByteString encodeDn(final DN dn) {
        return ByteString.valueOf(dn.toNormalizedString());
    }

    static byte[] encodeEntry(final Entry entry) throws IOException {
        final WriteBuffer buffer = threadLocalBuffer.get();
        buffer.builder.clearAndTruncate(2048, 2048);
        LDAP.writeEntry(buffer.asn1Writer, entry);
        return buffer.builder.toByteArray();
    }

    static LdapException internalError(final Exception e) {
        if (e instanceof LdapException) {
            return (LdapException) e;
        }
        return newErrorResult(ResultCode.OTHER, e);
    }

    private Util() {
    }

}
