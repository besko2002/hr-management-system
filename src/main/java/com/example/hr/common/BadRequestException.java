package com.example.hr.common;

/** The request is malformed or semantically invalid. Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
