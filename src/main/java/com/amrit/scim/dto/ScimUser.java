package com.amrit.scim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The SCIM User resource as defined in RFC 7643 §4.1.
 * <p>
 * This is the DTO (Data Transfer Object) that gets serialised/deserialised
 * by Jackson for every request and response body. It is deliberately kept
 * separate from {@link com.amrit.scim.entity.UserEntity} so that the API
 * contract can evolve independently from the database schema.
 * <p>
 * The {@code schemas} field is required by the SCIM spec on every response.
 * We default it here so the mapper never has to remember to set it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimUser {

    /**
     * SCIM schema URN — required on every resource representation.
     * Default value matches the Core User schema URN from RFC 7643.
     */
    @Builder.Default
    private List<String> schemas =
            List.of("urn:ietf:params:scim:schemas:core:2.0:User");

    /** Server-generated UUID. Never set by the client. */
    private String id;

    /**
     * The unique identifier for the user, typically an email address or
     * login name.  Required by the SCIM spec and enforced at the DB level.
     */
    @NotBlank(message = "userName is required")
    private String userName;

    /** Optional identifier supplied by the upstream IdP (Okta, Entra ID, …). */
    private String externalId;

    /**
     * The user's human name.  We use {@code @Valid} to cascade validation
     * into the nested object (e.g. checking email format inside ScimEmail).
     */
    @Valid
    private ScimName name;

    /** List of email addresses; may be empty but never null. */
    @Valid
    private List<ScimEmail> emails;

    /**
     * Whether the account is active.  Defaults to {@code true} per the SCIM
     * spec — clients that omit this field get an active user.
     */
    @Builder.Default
    private boolean active = true;

    /** Server-managed resource metadata (created, lastModified, location). */
    private ScimMeta meta;
}
