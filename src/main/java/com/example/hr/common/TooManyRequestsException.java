package com.example.hr.common;

/** The caller is being throttled. Maps to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
