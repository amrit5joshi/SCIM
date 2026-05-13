package com.amrit.scim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity that maps to the {@code user_emails} table.
 * <p>
 * Each row represents one email address belonging to a SCIM User.
 * The many-to-one relationship back to {@link UserEntity} is the owning side
 * (it holds the foreign key column {@code user_id}).
 */
@Entity
@Table(name = "user_emails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The actual email address value, e.g. "alice@example.com". */
    @Column(name = "email_value", nullable = false)
    private String value;

    /** SCIM email type: "work", "home", "other". */
    @Column(name = "type")
    private String type;

    /** Whether this is the user's primary email. At most one should be true per user. */
    @Column(name = "primary_email")
    private boolean primary;

    /**
     * The owning side of the relationship.
     * {@code @JoinColumn} names the FK column in the {@code user_emails} table.
     * We mark this field as not included in toString/equals to avoid circular references.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
}
