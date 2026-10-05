package com.example.hr.common;

/** The requested resource does not exist, or the caller may not know that it does. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
