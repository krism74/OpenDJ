package org.opendj.scratch.be.spi;

import java.io.Closeable;

import org.forgerock.opendj.ldap.ByteSequence;

@SuppressWarnings("javadoc")
public interface Importer extends Closeable {
    @Override
    void close();

    void createTree(TreeName name);

    void put(TreeName name, ByteSequence key, ByteSequence value);
}