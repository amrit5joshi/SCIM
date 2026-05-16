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
 * Converts between the JPA persistence model ({@link UserEntity} / {@link EmailEntity})
 * and the SCIM wire format ({@link ScimUser}).
 * <p>
 * Keeping mapping logic here prevents entities and DTOs from depending on each
 * other. Uses explicit field mapping rather than MapStruct for full transparency.
 */
@Component
public class UserMapper {

    /**
     * Converts a validated inbound {@link ScimUser} to a new {@link UserEntity}.
     * The server assigns the UUID and timestamps (SCIM spec §3.1).
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
     * Applies PUT body fields onto an existing entity in-place.
     * Preserves {@code id} and {@code created}; refreshes {@code lastModified}.
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

        entity.getEmails().clear();
        List<EmailEntity> newEmails = mapEmailDtosToEntities(dto.getEmails(), entity);
        entity.getEmails().addAll(newEmails);

        return entity;
    }

    /**
     * Converts a persisted {@link UserEntity} to a response {@link ScimUser},
     * building the absolute {@code meta.location} from the request base URL.
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
