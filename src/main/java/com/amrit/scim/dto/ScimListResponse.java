package com.amrit.scim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The SCIM ListResponse message (RFC 7644 §3.4.2).
 * <p>
 * Every paginated list endpoint wraps its results in this envelope so that
 * clients can implement cursor-free pagination using {@code startIndex} and
 * {@code totalResults}.  Note the capitalised {@code Resources} field —
 * that is what the SCIM spec requires, so we use {@code @JsonProperty} to
 * override Jackson's default camelCase serialisation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimListResponse {

    /** Always the ListResponse schema URN per RFC 7644. */
    @Builder.Default
    private List<String> schemas =
            List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    /** Total number of matching results across all pages. */
    private int totalResults;

    /**
     * 1-based index of the first result in this page.
     * Matches the {@code startIndex} query parameter supplied by the client.
     */
    private int startIndex;

    /** Number of results actually returned in this page. */
    private int itemsPerPage;

    /**
     * The list of User resources for this page.
     * Capital-R "Resources" is mandated by the SCIM spec — Jackson would
     * normally produce "resources", so @JsonProperty corrects that.
     */
    @JsonProperty("Resources")
    private List<ScimUser> resources;
}
