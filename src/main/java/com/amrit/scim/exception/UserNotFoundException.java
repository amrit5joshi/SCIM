package com.amrit.scim.exception;

/**
 * Thrown by {@link com.amrit.scim.service.UserService} when a caller requests
 * a user that does not exist in the database (GET, PUT, DELETE by id).
 * <p>
 * Caught by {@link com.amrit.scim.exception.ScimExceptionHandler} and mapped
 * to a 404 Not Found SCIM error response.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String id) {
        super("No user found with id '" + id + "'.");
    }
}
