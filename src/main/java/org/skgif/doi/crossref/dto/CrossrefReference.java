package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code work.reference[]} entry - Crossref's structured citation list, and (verified live:
 * plain works commonly have an empty {@code relation} map even when {@code reference[]} is
 * populated) the actual source of "this work cites DOI X", unlike DataCite where that comes
 * from {@code relatedIdentifiers[relationType=Cites]}. Only entries the depositing publisher
 * asserted a DOI for ({@code doi-asserted-by}) carry one; {@code key}/{@code unstructured} are
 * kept as a fallback label so a DOI-less reference can still get an otf id instead of being
 * dropped.
 *
 * @param doi          the cited work's DOI, when the publisher asserted one
 * @param key          the reference's own key, used as a fallback label when {@code doi} is absent
 * @param unstructured a free-text citation string, used as a fallback label when {@code doi}
 *                     is absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefReference(
        @JsonProperty("DOI") String doi,
        @JsonProperty("key") String key,
        @JsonProperty("unstructured") String unstructured) {
}
