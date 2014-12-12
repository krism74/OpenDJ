package org.opendj.scratch.be.spi;

@SuppressWarnings("javadoc")
public interface WriteOperation {
    void run(WriteableStorage storage) throws Exception;
}