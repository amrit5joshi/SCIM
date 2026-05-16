package com.amrit.scim.exception;

import com.amrit.scim.dto.ScimError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception → SCIM error response mapping.
 * <p>
 * Intercepts all exceptions and converts them to the SCIM error schema
 * (RFC 7644 §3.12) so that clients always receive a parseable JSON error
 * instead of Spring's default HTML error page.
 */
@Slf4j
@RestControllerAdvice
public class ScimExceptionHandler {

    /**
     * 404 — user not found.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ScimError> handleNotFound(UserNotFoundException ex) {
        log.debug("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ScimError.builder()
                        .status("404")
                        .detail(ex.getMessage())
                        .build());
    }

    /**
     * 409 — duplicate userName; {@code scimType = "uniqueness"} per RFC 7644 §3.12.
     */
    @ExceptionHandler(DuplicateUserNameException.class)
    public ResponseEntity<ScimError> handleDuplicate(DuplicateUserNameException ex) {
        log.debug("Duplicate userName: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ScimError.builder()
                        .status("409")
                        .scimType("uniqueness")
                        .detail(ex.getMessage())
                        .build());
    }

    /**
     * 400 — business-rule violation (e.g. multiple primary emails); {@code scimType = "invalidValue"}.
     */
    @ExceptionHandler(ScimValidationException.class)
    public ResponseEntity<ScimError> handleValidationRule(ScimValidationException ex) {
        log.debug("SCIM validation failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ScimError.builder()
                        .status("400")
                        .scimType("invalidValue")
                        .detail(ex.getMessage())
                        .build());
    }

    /**
     * 400 — unsupported filter expression; {@code scimType = "invalidFilter"}.
     */
    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<ScimError> handleInvalidFilter(InvalidFilterException ex) {
        log.debug("Invalid filter: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ScimError.builder()
                        .status("400")
                        .scimType("invalidFilter")
                        .detail(ex.getMessage())
                        .build());
    }

    /**
     * 400 — Jakarta Bean Validation failed (e.g. missing {@code userName}).
     * Collects all field-level messages into one human-readable string.
     * {@code scimType = "invalidValue"} per RFC 7644.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ScimError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.debug("Validation failed: {}", detail);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ScimError.builder()
                        .status("400")
                        .scimType("invalidValue")
                        .detail(detail)
                        .build());
    }

    /**
     * Catch-all 500 — something unexpected happened.
     * We log the full stack trace at ERROR level but return a generic message
     * to the client (never leak internal details).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScimError> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ScimError.builder()
                        .status("500")
                        .detail("An internal server error occurred.")
                        .build());
    }
}
