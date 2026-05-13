package com.amrit.scim.exception;

/**
 * Thrown by {@link com.amrit.scim.filter.ScimFilterParser} when the caller
 * supplies a {@code filter} query parameter that is not a supported
 * {@code userName eq "..."} expression.
 * <p>
 * Caught by {@link ScimExceptionHandler} and mapped to a 400 Bad Request
 * SCIM error with {@code scimType = "invalidFilter"}.
 */
public class InvalidFilterException extends RuntimeException {

    public InvalidFilterException(String message) {
        super(message);
    }
}
