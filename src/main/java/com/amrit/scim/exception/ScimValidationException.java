package com.amrit.scim.exception;

/**
 * Thrown when a request body violates a SCIM business rule that cannot be
 * expressed as a Jakarta Bean Validation constraint — for example, having
 * more than one email address flagged as {@code primary}.
 * Mapped to 400 Bad Request with {@code scimType = "invalidValue"}.
 */
public class ScimValidationException extends RuntimeException {

    public ScimValidationException(String message) {
        super(message);
    }
}
