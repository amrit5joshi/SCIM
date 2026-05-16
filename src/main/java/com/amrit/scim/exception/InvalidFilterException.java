package com.amrit.scim.exception;

/**
 * Thrown when the {@code filter} query parameter cannot be parsed as a
 * supported {@code userName eq} or {@code externalId eq} expression.
 * Mapped to 400 Bad Request with {@code scimType = "invalidFilter"}.
 */
public class InvalidFilterException extends RuntimeException {

    public InvalidFilterException(String message) {
        super(message);
    }
}
