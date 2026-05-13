package com.amrit.scim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the SCIM {@code name} complex attribute (RFC 7643 §4.1.1).
 * <p>
 * This is a nested JSON object inside {@link ScimUser}, not a top-level
 * resource — it exists purely as a data-carrier (DTO) between the JSON
 * wire format and the mapper layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimName {

    /** The user's first / given name, e.g. "Alice". */
    private String givenName;

    /** The user's last / family name, e.g. "Smith". */
    private String familyName;

    /**
     * The full display name, e.g. "Alice Smith".
     * Identity providers often compute this automatically; we store it as-is.
     */
    private String formatted;
}
