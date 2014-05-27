package org.opendj.scratch.be;

import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.File;
import java.io.IOException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;

final class Util {
    private static final class WriteBuffer {
        private final ByteStringBuilder builder;
        private final ASN1Writer asn1Writer;

        private WriteBuffer() {
            builder = new ByteStringBuilder();
            asn1Writer = ASN1.getWriter(builder, 2048);
        }
    }

    private static final ThreadLocal<WriteBuffer> threadLocalBuffer =
            new ThreadLocal<WriteBuffer>() {
                @Override
                protected WriteBuffer initialValue() {
                    return new WriteBuffer();
                };
            };

    static void createDbDir(final File dbDir) {
        if (dbDir.exists()) {
            for (final File child : dbDir.listFiles()) {
                child.delete();
            }
        } else {
            dbDir.mkdirs();
        }
    }

    static byte[] encodeEntry(final Entry entry) throws IOException {
        final WriteBuffer buffer = threadLocalBuffer.get();
        buffer.builder.clearAndTruncate(2048, 2048);
        LDAP.writeEntry(buffer.asn1Writer, entry);
        return buffer.builder.toByteArray();
    }

    static ErrorResultException internalError(final Exception e) {
        if (e instanceof ErrorResultException) {
            return (ErrorResultException) e;
        }
        return newErrorResult(ResultCode.OTHER, e);
    }

    private Util() {
    }

}
