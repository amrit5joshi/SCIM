package com.amrit.scim.controller;

import com.amrit.scim.dto.ScimListResponse;
import com.amrit.scim.dto.ScimUser;
import com.amrit.scim.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for SCIM 2.0 User endpoints (RFC 7644 §3.3–3.6).
 * <p>
 * Exposes five operations under {@code /scim/v2/Users}: Create, Read, List,
 * Replace, and Delete. Accepts both {@code application/json} and
 * {@code application/scim+json}. Error handling is centralised in
 * {@link com.amrit.scim.exception.ScimExceptionHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/scim/v2/Users")
@RequiredArgsConstructor
@Tag(name = "SCIM Users", description = "SCIM 2.0 User provisioning endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // -------------------------------------------------------------------------
    // POST /scim/v2/Users  — Create
    // -------------------------------------------------------------------------

    /** Creates a user; returns 201 with server-assigned {@code id} and {@code meta} (RFC 7644 §3.3). */
    @Operation(summary = "Create a user", description = "Creates a new SCIM User. Returns 201 with the created resource.")
    @PostMapping(
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"},
            produces = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"}
    )
    public ResponseEntity<ScimUser> createUser(
            @Valid @RequestBody ScimUser scimUser,
            HttpServletRequest request) {

        String baseUrl = extractBaseUrl(request);
        ScimUser created = userService.createUser(scimUser, baseUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // GET /scim/v2/Users/{id}  — Read
    // -------------------------------------------------------------------------

    /** Returns a single user by UUID, or a 404 SCIM error if not found. */
    @Operation(summary = "Get a user by ID", description = "Returns a single User resource by UUID.")
    @GetMapping(
            value = "/{id}",
            produces = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"}
    )
    public ResponseEntity<ScimUser> getUser(
            @Parameter(description = "Server-generated user UUID") @PathVariable String id,
            HttpServletRequest request) {

        String baseUrl = extractBaseUrl(request);
        ScimUser user = userService.getUser(id, baseUrl);
        return ResponseEntity.ok(user);
    }

    // -------------------------------------------------------------------------
    // GET /scim/v2/Users  — List with filter + pagination
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated {@code ListResponse} (RFC 7644 §3.4.2).
     * Supports {@code filter=userName eq "value"} and {@code filter=externalId eq "value"}.
     * {@code startIndex} is 1-based; {@code count} is capped at 100.
     */
    @Operation(summary = "List users", description = "Returns a paginated ListResponse. Supports filter=userName eq \"value\".")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"})
    public ResponseEntity<ScimListResponse> listUsers(
            @Parameter(description = "SCIM filter: userName eq \"value\"")
            @RequestParam(required = false) String filter,

            @Parameter(description = "1-based start index (default 1)")
            @RequestParam(defaultValue = "1") int startIndex,

            @Parameter(description = "Number of results to return (default 10, max 100)")
            @RequestParam(defaultValue = "10") int count,

            HttpServletRequest request) {

        String baseUrl = extractBaseUrl(request);
        ScimListResponse response = userService.listUsers(filter, startIndex, count, baseUrl);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // PUT /scim/v2/Users/{id}  — Replace
    // -------------------------------------------------------------------------

    /** Full replacement of a user resource (RFC 7644 §3.5.1). Returns 200 with updated resource. */
    @Operation(summary = "Replace a user", description = "Full PUT replacement of an existing User resource.")
    @PutMapping(
            value = "/{id}",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"},
            produces = {MediaType.APPLICATION_JSON_VALUE, "application/scim+json"}
    )
    public ResponseEntity<ScimUser> replaceUser(
            @PathVariable String id,
            @Valid @RequestBody ScimUser scimUser,
            HttpServletRequest request) {

        String baseUrl = extractBaseUrl(request);
        ScimUser updated = userService.replaceUser(id, scimUser, baseUrl);
        return ResponseEntity.ok(updated);
    }

    // -------------------------------------------------------------------------
    // DELETE /scim/v2/Users/{id}  — Delete
    // -------------------------------------------------------------------------

    /** Hard-deletes a user by UUID; returns 204 No Content (RFC 7644 §3.6). */
    @Operation(summary = "Delete a user", description = "Hard-deletes a User resource. Returns 204 No Content.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Builds the scheme+host+port prefix used for {@code meta.location}, e.g. {@code http://localhost:8080}. */
    private String extractBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if ((scheme.equals("http") && port == 80) ||
            (scheme.equals("https") && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}
