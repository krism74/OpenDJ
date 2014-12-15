package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface WriteableStorage extends ReadableStorage {
    void put(TreeName treeName, ByteSequence key, ByteSequence value);

    boolean putIfAbsent(TreeName treeName, ByteSequence key, ByteSequence value);

    boolean remove(TreeName treeName, ByteSequence key);
}