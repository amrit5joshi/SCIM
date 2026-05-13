package com.amrit.scim.exception;

/**
 * Thrown by {@link com.amrit.scim.service.UserService} when a caller tries
 * to create a user with a {@code userName} that already exists in the database.
 * <p>
 * Caught by {@link com.amrit.scim.exception.ScimExceptionHandler} and mapped
 * to a 409 Conflict SCIM error with {@code scimType = "uniqueness"}.
 */
public class DuplicateUserNameException extends RuntimeException {

    public DuplicateUserNameException(String userName) {
        super("A user with userName '" + userName + "' already exists.");
    }
}
