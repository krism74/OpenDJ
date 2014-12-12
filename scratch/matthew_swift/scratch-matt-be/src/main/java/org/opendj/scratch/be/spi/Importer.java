package org.opendj.scratch.be.spi;

import java.io.Closeable;

import org.forgerock.opendj.ldap.ByteString;

@SuppressWarnings("javadoc")
public interface Importer extends Closeable {
    @Override
    void close();

    void createTree(TreeName name);

    void put(TreeName name, ByteString key, ByteString value);
}