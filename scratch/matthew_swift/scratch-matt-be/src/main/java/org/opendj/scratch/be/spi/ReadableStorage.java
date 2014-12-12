package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public interface ReadableStorage {
    ByteString get(TreeName name, ByteString key);

    ByteString getRMW(TreeName name, ByteString key);

    // TODO: cursoring, contains, etc.
}