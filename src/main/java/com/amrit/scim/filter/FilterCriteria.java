package com.amrit.scim.filter;

/**
 * Parsed result of a SCIM filter expression.
 * Carries the attribute name and the equality value so the service layer
 * can route to the correct repository query.
 *
 * @param attribute the SCIM attribute being filtered (e.g. "userName", "externalId")
 * @param value     the exact value to match
 */
public record FilterCriteria(String attribute, String value) {
}
