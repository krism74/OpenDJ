package org.opendj.scratch.be.spi;

@SuppressWarnings({ "serial", "javadoc" })
public final class StorageRuntimeException extends RuntimeException {

    public StorageRuntimeException(final String message) {
        super(message);
    }

    public StorageRuntimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public StorageRuntimeException(final Throwable cause) {
        super(cause);
    }
}