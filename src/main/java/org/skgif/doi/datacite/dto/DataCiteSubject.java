package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One {@code subjects[]} entry, with its optional scheme and language.
 *
 * @param subject       the subject keyword
 * @param subjectScheme the controlled vocabulary it comes from, if any
 * @param lang          the keyword's language tag, if given
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteSubject(
        @Nullable String subject,
        @Nullable String subjectScheme,
        @Nullable String lang) {
}
