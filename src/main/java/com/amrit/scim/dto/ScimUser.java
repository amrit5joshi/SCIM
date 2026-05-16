package com.amrit.scim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SCIM User resource (RFC 7643 §4.1) — the API contract DTO.
 * <p>
 * Kept separate from {@link com.amrit.scim.entity.UserEntity} so that the
 * API shape and the DB schema can evolve independently. The {@code schemas}
 * field is defaulted here so the mapper never needs to set it explicitly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimUser {

    /** Core User schema URN required on every SCIM resource (RFC 7643). */
    @Builder.Default
    private List<String> schemas =
            List.of("urn:ietf:params:scim:schemas:core:2.0:User");

    /** Server-generated UUID; never set by the client. */
    private String id;

    /** Required unique identifier (RFC 7643 §4.1.1); enforced at DB level via UNIQUE constraint. */
    @NotBlank(message = "userName is required")
    private String userName;

    /** Optional identifier supplied by the upstream IdP. */
    private String externalId;

    /** Cascades Bean Validation into the nested name and email objects. */
    @Valid
    private ScimName name;

    /** Email addresses; at most one should carry {@code primary=true}. */
    @Valid
    private List<ScimEmail> emails;

    /** Account active flag; defaults to {@code true} per SCIM spec. */
    @Builder.Default
    private boolean active = true;

    /** Server-managed resource metadata. */
    private ScimMeta meta;
}
