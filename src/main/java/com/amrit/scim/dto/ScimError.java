package com.amrit.scim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The SCIM Error response schema (RFC 7644 §3.12).
 * <p>
 * Every error — validation failure, not-found, duplicate, or missing auth —
 * is returned in this shape.  Using a single, consistent error format makes
 * it easy for SCIM clients (Okta, Entra ID) to parse failures without
 * special-casing our service. The {@code scimType} field carries a
 * machine-readable sub-code (e.g. "uniqueness", "invalidValue").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimError {

    /** Always the Error schema URN; required by the spec. */
    @Builder.Default
    private List<String> schemas =
            List.of("urn:ietf:params:scim:api:messages:2.0:Error");

    /**
     * The HTTP status code as a string, e.g. "400", "404", "409".
     * The spec requires this inside the body even though it duplicates the
     * HTTP response status line — clients sometimes read bodies in isolation.
     */
    private String status;

    /**
     * Optional machine-readable error sub-type defined by RFC 7644 §3.12.
     * Examples: "uniqueness", "invalidValue", "invalidFilter".
     * May be {@code null} for generic errors like 401/404.
     */
    private String scimType;

    /** Human-readable description of what went wrong. */
    private String detail;
}
