package com.amrit.scim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity that maps to the {@code users} table in MySQL.
 * <p>
 * This is the persistence model — it reflects the DB schema, NOT the SCIM wire
 * format. The mapper layer converts between this entity and the {@link com.amrit.scim.dto.ScimUser} DTO.
 * Keeping them separate means a DB schema change doesn't break the API contract
 * (and vice-versa) — an important design point for interviews.
 * <p>
 * Email design choice: emails are stored in a separate {@code user_emails} table
 * (a {@code @OneToMany} relationship) rather than a JSON column. This lets MySQL
 * enforce referential integrity and makes it easy to query "all users with a
 * given email address" later. The trade-off is a JOIN on every read; for a
 * provisioning service with modest load that is perfectly acceptable.
 */
@Entity
@Table(name = "users")
@Data                   // Lombok: generates getters, setters, equals, hashCode, toString
@Builder                // Lombok: gives us a fluent UserEntity.builder()... pattern
@NoArgsConstructor      // JPA requires a no-arg constructor
@AllArgsConstructor     // needed by @Builder when @NoArgsConstructor is also present
public class UserEntity {

    /**
     * Server-generated UUID — SCIM spec requires the server to own the id.
     * We store it as a VARCHAR(36) because MySQL has no native UUID type before 8.0.28.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /**
     * SCIM userName — must be unique across the tenant.
     * The UNIQUE constraint is enforced at the DB level (belt-and-suspenders
     * alongside the application-level duplicate check).
     */
    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    /** Caller-supplied identifier from the upstream IdP (Okta objectId, etc.). */
    @Column(name = "external_id")
    private String externalId;

    // ---- name sub-object (flattened into the same table row) ----
    // SCIM name is a complex attribute but all three fields fit comfortably
    // in the users table — no need for a separate table.

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

    // ---- meta timestamps ----

    @Column(name = "created", nullable = false, updatable = false)
    private Instant created;

    @Column(name = "last_modified", nullable = false)
    private Instant lastModified;

    // ---- emails (separate table, one-to-many) ----

    /**
     * CascadeType.ALL means when we save/delete a UserEntity, JPA automatically
     * saves/deletes its EmailEntity children too — no manual email management needed.
     * orphanRemoval = true ensures emails removed from the list are deleted from DB.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<EmailEntity> emails;
}
