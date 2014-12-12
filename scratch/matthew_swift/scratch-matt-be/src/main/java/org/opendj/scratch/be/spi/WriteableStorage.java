package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public interface WriteableStorage extends ReadableStorage {
    void put(TreeName name, ByteString key, ByteString value);

    // FIXME implement
    // boolean putIfAbsent(TreeName treeName, ByteString key, ByteString value);

    boolean remove(TreeName name, ByteString key);

    // FIXME implement
    // boolean remove(TreeName name, ByteString key, ByteString value);
}