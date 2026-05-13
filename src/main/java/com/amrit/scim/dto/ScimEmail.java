package com.amrit.scim.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one entry in the SCIM {@code emails} multi-valued attribute (RFC 7643 §4.1.2).
 * <p>
 * Each SCIM User may have multiple email addresses, each tagged with a type
 * ("work", "home", "other") and a {@code primary} flag.  This DTO is an
 * element in the {@code ScimUser.emails} list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimEmail {

    /** The actual email address; validated with Jakarta's @Email constraint. */
    @Email(message = "emails[].value must be a valid email address")
    private String value;

    /** SCIM canonical type: "work", "home", or "other". */
    private String type;

    /**
     * True if this is the user's primary email address.
     * SCIM requires at most one email per user to have {@code primary=true}.
     */
    private boolean primary;
}
