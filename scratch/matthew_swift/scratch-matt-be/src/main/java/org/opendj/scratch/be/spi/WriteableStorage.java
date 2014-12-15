package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface WriteableStorage extends ReadableStorage {
    void put(TreeName name, ByteSequence key, ByteSequence value);

    // FIXME implement
    // boolean putIfAbsent(TreeName treeName, ByteSequence key, ByteSequence value);

    boolean remove(TreeName name, ByteSequence key);

    // FIXME implement
    // boolean remove(TreeName name, ByteSequence key, ByteSequence value);
}