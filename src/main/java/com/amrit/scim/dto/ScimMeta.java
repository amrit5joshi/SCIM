package com.amrit.scim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents the SCIM {@code meta} complex attribute (RFC 7643 §3.1).
 * <p>
 * {@code meta} is a read-only, server-managed object included in every SCIM
 * resource response.  Clients should never send {@code meta} in request
 * bodies — if they do, the server ignores it (we simply never read it from
 * incoming JSON).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimMeta {

    /** Always "User" for our User resources. */
    private String resourceType;

    /** Timestamp when the resource was first created (server-managed). */
    private Instant created;

    /** Timestamp of the most recent modification (server-managed). */
    private Instant lastModified;

    /**
     * The canonical URL of this resource, e.g.
     * {@code http://localhost:8080/scim/v2/Users/some-uuid}.
     * Populated by the mapper using the request's base URL.
     */
    private String location;
}
