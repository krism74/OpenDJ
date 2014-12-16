package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public interface ReadableStorage {
    ByteString read(TreeName treeName, ByteSequence key);
}
