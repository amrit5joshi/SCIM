package com.amrit.scim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code users} table.
 * <p>
 * Kept deliberately separate from the SCIM DTO so that DB schema changes don't
 * ripple into the API contract. Emails are stored in a child table rather than
 * a JSON column to preserve referential integrity and allow indexed lookups.
 */
@Entity
@Table(name = "users")
@Data                   // Lombok: generates getters, setters, equals, hashCode, toString
@Builder                // Lombok: gives us a fluent UserEntity.builder()... pattern
@NoArgsConstructor      // JPA requires a no-arg constructor
@AllArgsConstructor     // needed by @Builder when @NoArgsConstructor is also present
public class UserEntity {

    /** Server-generated UUID; stored as VARCHAR(36) for cross-DB portability. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** SCIM userName — unique at the DB level. Application-level check provides a clean 409 before hitting the constraint. */
    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    /** Caller-supplied identifier from the upstream IdP. */
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "name_given")
    private String givenName;

    @Column(name = "name_family")
    private String familyName;

    @Column(name = "name_formatted")
    private String formattedName;

    /** Whether the account is active. Defaults to true per SCIM spec. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created", nullable = false, updatable = false)
    private Instant created;

    @Column(name = "last_modified", nullable = false)
    private Instant lastModified;

    /**
     * Lazy-loaded with @BatchSize to avoid N+1 on list queries.
     * Hibernate issues one batched SELECT for up to 25 users rather than one per user.
     * Single-user reads use @EntityGraph in the repository for a JOIN FETCH instead.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    private List<EmailEntity> emails;
}
