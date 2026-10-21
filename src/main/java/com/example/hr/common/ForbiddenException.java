package com.example.hr.common;

/**
 * The caller is authenticated but not allowed to perform this action. Maps to HTTP 403.
 *
 * <p>Used only where the existence of the resource is not itself a secret. Record-level
 * read access uses {@link ResourceNotFoundException} instead, so that employee ids of
 * people outside the caller's scope cannot be probed.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
