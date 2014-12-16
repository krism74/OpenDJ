package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface UpdateFunction {
    /**
     * Computes the new value for a record based on the record's existing
     * content.
     *
     * @param oldValue
     *            The record's existing content, or {@code null} if the record
     *            does not exist at the moment and is about to be created.
     * @return The new value for the record, or {@code null} if the record
     *         should be completely removed.
     */
    ByteSequence computeNewValue(ByteSequence oldValue);
}
