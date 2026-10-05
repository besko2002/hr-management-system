package com.example.hr.common;

/** The request conflicts with the current state of the resource. Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
