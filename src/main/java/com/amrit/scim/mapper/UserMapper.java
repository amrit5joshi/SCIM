package com.amrit.scim.mapper;

import com.amrit.scim.dto.*;
import com.amrit.scim.entity.EmailEntity;
import com.amrit.scim.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts between the persistence model ({@link UserEntity} / {@link EmailEntity})
 * and the SCIM wire format ({@link ScimUser}).
 * <p>
 * Keeping mapping logic here — rather than inside the entity or DTO — follows
 * the Single-Responsibility Principle: entities know about persistence,
 * DTOs know about JSON, and the mapper knows about the translation between them.
 * This is the key design decision to explain in an interview.
 * <p>
 * We use a plain {@code @Component} (not MapStruct) so that every field
 * mapping is explicit and readable — important for a learning project.
 */
@Component
public class UserMapper {

    /**
     * Converts an inbound {@link ScimUser} request body into a new
     * {@link UserEntity} ready to be persisted for the first time.
     * <p>
     * The server assigns the UUID and timestamps — the client must never
     * control these values (SCIM spec §3.1).
     *
     * @param dto the validated inbound SCIM User
     * @return a fully populated, unsaved UserEntity
     */
    public UserEntity toNewEntity(ScimUser dto) {
        Instant now = Instant.now();
        String newId = UUID.randomUUID().toString();

        UserEntity entity = UserEntity.builder()
                .id(newId)
                .userName(dto.getUserName())
                .externalId(dto.getExternalId())
                .givenName(dto.getName() != null ? dto.getName().getGivenName() : null)
                .familyName(dto.getName() != null ? dto.getName().getFamilyName() : null)
                .formattedName(dto.getName() != null ? dto.getName().getFormatted() : null)
                .active(dto.isActive())
                .created(now)
                .lastModified(now)
                .build();

        List<EmailEntity> emails = mapEmailDtosToEntities(dto.getEmails(), entity);
        entity.setEmails(emails);

        return entity;
    }

    /**
     * Applies the fields from an inbound PUT request body onto an existing
     * {@link UserEntity}.  The {@code id}, {@code created} timestamps are
     * preserved; {@code lastModified} is refreshed to now.
     *
     * @param entity the existing entity loaded from the DB
     * @param dto    the inbound replacement body
     * @return the same entity instance with updated fields (mutated in-place)
     */
    public UserEntity applyUpdate(UserEntity entity, ScimUser dto) {
        entity.setUserName(dto.getUserName());
        entity.setExternalId(dto.getExternalId());
        entity.setActive(dto.isActive());
        entity.setLastModified(Instant.now());

        if (dto.getName() != null) {
            entity.setGivenName(dto.getName().getGivenName());
            entity.setFamilyName(dto.getName().getFamilyName());
            entity.setFormattedName(dto.getName().getFormatted());
        } else {
            entity.setGivenName(null);
            entity.setFamilyName(null);
            entity.setFormattedName(null);
        }

        // Replace email list entirely — orphanRemoval on the entity handles deletion
        entity.getEmails().clear();
        List<EmailEntity> newEmails = mapEmailDtosToEntities(dto.getEmails(), entity);
        entity.getEmails().addAll(newEmails);

        return entity;
    }

    /**
     * Converts a persisted {@link UserEntity} to a {@link ScimUser} DTO for
     * the response body, populating the {@code meta.location} field with the
     * canonical URL of this resource.
     *
     * @param entity      the entity loaded from the DB
     * @param baseUrl     the scheme+host+port of the current request,
     *                    e.g. {@code http://localhost:8080}
     * @return a fully populated ScimUser ready to serialise as JSON
     */
    public ScimUser toDto(UserEntity entity, String baseUrl) {
        ScimName name = ScimName.builder()
                .givenName(entity.getGivenName())
                .familyName(entity.getFamilyName())
                .formatted(entity.getFormattedName())
                .build();

        ScimMeta meta = ScimMeta.builder()
                .resourceType("User")
                .created(entity.getCreated())
                .lastModified(entity.getLastModified())
                .location(baseUrl + "/scim/v2/Users/" + entity.getId())
                .build();

        List<ScimEmail> emails = entity.getEmails() == null
                ? Collections.emptyList()
                : entity.getEmails().stream()
                        .map(e -> ScimEmail.builder()
                                .value(e.getValue())
                                .type(e.getType())
                                .primary(e.isPrimary())
                                .build())
                        .collect(Collectors.toList());

        return ScimUser.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .externalId(entity.getExternalId())
                .name(name)
                .emails(emails)
                .active(entity.isActive())
                .meta(meta)
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a list of {@link ScimEmail} DTOs to {@link EmailEntity} objects,
     * wiring each entity back to its parent user so Hibernate can persist the FK.
     */
    private List<EmailEntity> mapEmailDtosToEntities(List<ScimEmail> dtos,
                                                      UserEntity parent) {
        if (dtos == null || dtos.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return dtos.stream()
                .map(d -> EmailEntity.builder()
                        .value(d.getValue())
                        .type(d.getType())
                        .primary(d.isPrimary())
                        .user(parent)
                        .build())
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }
}
