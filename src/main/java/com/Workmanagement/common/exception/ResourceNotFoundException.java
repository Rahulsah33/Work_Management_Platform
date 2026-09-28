package com.Workmanagement.common.exception;

/**
 * Thrown when a requested entity does not exist.
 * The GlobalExceptionHandler maps this to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
