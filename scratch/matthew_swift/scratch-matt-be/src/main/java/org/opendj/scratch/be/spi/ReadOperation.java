package org.opendj.scratch.be.spi;


@SuppressWarnings("javadoc")
public interface ReadOperation<T> {
    T run(ReadableStorage storage) throws Exception;
}