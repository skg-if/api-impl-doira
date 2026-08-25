package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The {@code {id, id-type, asserted-by}} shape Crossref uses for structured external
 * identifiers - shared by affiliation ids (usually a ROR) and funder ids (usually a Funder
 * Registry DOI), e.g. {@code affiliation[].id[]} and grant {@code project[].funding[].funder.id[]}.
 *
 * @param id         the identifier value
 * @param idType     the identifier scheme (e.g. {@code "ROR"}, {@code "DOI"})
 * @param assertedBy who asserted this identifier ({@code "publisher"} or {@code "crossref"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefIdEntry(
        @Nullable String id,
        @JsonProperty("id-type") @Nullable String idType,
        @JsonProperty("asserted-by") @Nullable String assertedBy) {
}
