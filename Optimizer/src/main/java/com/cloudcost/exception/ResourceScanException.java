package com.cloudcost.exception;

public class ResourceScanException extends ResourceException {
    public ResourceScanException(String message) {
        super(message);
    }

    public ResourceScanException(String message, Throwable cause) {
        super(message, cause);
    }
}