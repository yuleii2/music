package com.k2.music;

/** Unchecked failure from a local, offline data store. */
public final class LocalStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocalStoreException(String message) {
        super(message);
    }

    public LocalStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
