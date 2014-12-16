package org.opendj.scratch.be.spi;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface WriteableStorage extends ReadableStorage {
    void create(TreeName treeName, ByteSequence key, ByteSequence value);

    void update(TreeName treeName, ByteSequence key, UpdateFunction f);

    void delete(TreeName treeName, ByteSequence key);
}
