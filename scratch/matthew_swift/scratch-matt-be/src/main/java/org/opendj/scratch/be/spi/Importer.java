package org.opendj.scratch.be.spi;

import java.io.Closeable;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface Importer extends Closeable {
    @Override
    void close();

    void createTree(TreeName treeName);

    void put(TreeName treeName, ByteSequence key, ByteSequence value);
}