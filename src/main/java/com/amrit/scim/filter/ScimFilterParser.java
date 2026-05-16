package com.amrit.scim.filter;

import com.amrit.scim.exception.InvalidFilterException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SCIM {@code filter} query parameter (RFC 7644 §3.4.2.2).
 * <p>
 * Supports {@code userName eq "value"} and {@code externalId eq "value"}.
 * Any other expression is rejected with a 400 SCIM error. A full
 * implementation would use an ANTLR grammar or the {@code scim2-sdk} library.
 */
@Component
public class ScimFilterParser {

    /**
     * Matches {@code <attribute> eq "value"} for the two supported attributes.
     * Group 1 = attribute name, group 2 = value inside quotes.
     */
    private static final Pattern EQ_PATTERN =
            Pattern.compile("^(userName|externalId)\\s+eq\\s+\"([^\"]+)\"$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Parses the filter string and returns a {@link FilterCriteria} describing
     * which attribute to match and the exact value.
     *
     * @param filter the raw {@code filter} query-parameter value
     * @return an {@link Optional} containing the criteria, or empty if filter is null/blank
     * @throws InvalidFilterException if the filter is non-blank but not supported
     */
    public Optional<FilterCriteria> parse(String filter) {
        if (filter == null || filter.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = EQ_PATTERN.matcher(filter.trim());
        if (!matcher.matches()) {
            throw new InvalidFilterException(
                    "Only 'userName eq \"value\"' and 'externalId eq \"value\"' are supported. Got: " + filter);
        }

        return Optional.of(new FilterCriteria(matcher.group(1).toLowerCase(), matcher.group(2)));
    }
}
