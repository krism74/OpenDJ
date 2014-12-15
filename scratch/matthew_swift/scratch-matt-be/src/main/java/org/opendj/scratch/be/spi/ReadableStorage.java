package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public interface ReadableStorage {
    ByteString get(TreeName name, ByteSequence key);

    ByteString getRMW(TreeName name, ByteSequence key);

    // TODO: cursoring, contains, etc.
}