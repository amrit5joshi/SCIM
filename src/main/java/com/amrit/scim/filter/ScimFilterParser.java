package com.amrit.scim.filter;

import com.amrit.scim.exception.InvalidFilterException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SCIM {@code filter} query parameter (RFC 7644 §3.4.2.2).
 * <p>
 * Supports {@code userName eq "value"} only. Any other expression is rejected
 * with a 400 SCIM error. A full implementation would use an ANTLR grammar
 * or the {@code scim2-sdk} library to handle the complete filter grammar.
 */
@Component
public class ScimFilterParser {

    /**
     * Matches {@code userName eq "someValue"}, case-insensitive on attribute name and operator.
     * Group 1 captures the value inside the quotes.
     */
    private static final Pattern USERNAME_EQ_PATTERN =
            Pattern.compile("^userName\\s+eq\\s+\"([^\"]+)\"$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Parses the filter string and returns the userName value to match.
     *
     * @param filter the raw {@code filter} query-parameter value
     * @return an {@link Optional} containing the userName, or empty if
     *         {@code filter} is null/blank (meaning "no filter — list all")
     * @throws InvalidFilterException if the filter is non-blank but not a
     *         supported {@code userName eq "..."} expression
     */
    public Optional<String> parseUserNameFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = USERNAME_EQ_PATTERN.matcher(filter.trim());
        if (!matcher.matches()) {
            throw new InvalidFilterException(
                    "Only 'userName eq \"value\"' filters are supported. Got: " + filter);
        }

        return Optional.of(matcher.group(1));
    }
}
