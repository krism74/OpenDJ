package org.opendj.scratch.be.spi;

import java.io.Closeable;
import java.util.Map;

@SuppressWarnings("javadoc")
public interface Storage extends Closeable {
    void initialize(Map<String, String> options) throws Exception;

    Importer startImport() throws Exception;

    void open() throws Exception;

    void openTree(TreeName name);

    <T> T read(ReadOperation<T> operation) throws Exception;

    void write(WriteOperation operation) throws Exception;

    @Override
    void close();
}
