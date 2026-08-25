package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A single Crossref {@code works} record ({@code message} in the API envelope). Unlike
 * DataCite's JSON:API response (camelCase keys, matched by Jackson without annotations),
 * Crossref's own JSON uses kebab-case/all-caps keys, so every non-matching field needs an
 * explicit {@link JsonProperty}.
 *
 * <p>{@code award} and {@code project} are only present on grant-type records ({@code
 * type: "grant"} - see {@code CrossrefTypeMapping#isGrant}); every other field is a regular
 * work (article, dataset, book, etc).
 *
 * @param doi             the work's DOI
 * @param url             the work's resolvable URL
 * @param type            the Crossref work type (e.g. {@code "journal-article"}, {@code "grant"})
 * @param publisher       the publisher name
 * @param title           the work's title(s)
 * @param subtitle        the work's subtitle(s)
 * @param containerTitle  the container (e.g. journal, book series) title(s)
 * @param page            the page range
 * @param volume          the volume
 * @param issue           the issue
 * @param abstractText    the abstract
 * @param subject         the subject terms
 * @param issn            the container's ISSN(s)
 * @param author          the work's authors
 * @param editor          the work's editors
 * @param funder          the work's funders
 * @param license         the work's license(s)
 * @param reference       the work's structured citation list
 * @param relation        related-work identifiers, keyed by relation type (e.g.
 *                        {@code "is-supplemented-by"})
 * @param issued          the issue date
 * @param created         the record creation date
 * @param deposited       the last deposit date
 * @param publishedPrint  the print publication date
 * @param publishedOnline the online publication date
 * @param accepted        the acceptance date
 * @param updateTo        corrections/retractions/etc. applied to this work
 * @param award           the grant number, for grant-type records only
 * @param project         the grant's project(s), for grant-type records only
 * @param resource        the work's primary resource, for grant-type records only
 */
// Top-level Jackson DTO for an entire Crossref work/DOI record - necessarily has many
// independent optional fields; splitting it wouldn't help since Jackson needs one class
// matching the source JSON's shape.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWork(
        @JsonProperty("DOI") @Nullable String doi,
        @JsonProperty("URL") @Nullable String url,
        @Nullable String type,
        @Nullable String publisher,
        @Nullable List<String> title,
        @Nullable List<String> subtitle,
        @JsonProperty("container-title") @Nullable List<String> containerTitle,
        @Nullable String page,
        @Nullable String volume,
        @Nullable String issue,
        @JsonProperty("abstract") @Nullable String abstractText,
        @Nullable List<String> subject,
        @JsonProperty("ISSN") @Nullable List<String> issn,
        @Nullable List<CrossrefContributor> author,
        @Nullable List<CrossrefContributor> editor,
        @Nullable List<CrossrefFunder> funder,
        @Nullable List<CrossrefLicense> license,
        @Nullable List<CrossrefReference> reference,
        @Nullable Map<String, List<CrossrefIdEntry>> relation,
        @Nullable CrossrefDate issued,
        @Nullable CrossrefDate created,
        @Nullable CrossrefDate deposited,
        @JsonProperty("published-print") @Nullable CrossrefDate publishedPrint,
        @JsonProperty("published-online") @Nullable CrossrefDate publishedOnline,
        @Nullable CrossrefDate accepted,
        @JsonProperty("update-to") @Nullable List<CrossrefUpdateTo> updateTo,
        @Nullable String award,
        @Nullable List<CrossrefProject> project,
        @Nullable CrossrefResource resource) {
}
