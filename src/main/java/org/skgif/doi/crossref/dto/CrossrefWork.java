package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWork(
                           @JsonProperty("DOI") String doi,
                           @JsonProperty("URL") String url,
                           String type,
                           String publisher,
                           List<String> title,
                           List<String> subtitle,
                           @JsonProperty("container-title") List<String> containerTitle,
                           String page,
                           String volume,
                           String issue,
                           @JsonProperty("abstract") String abstractText,
                           List<String> subject,
                           @JsonProperty("ISSN") List<String> issn,
                           List<CrossrefContributor> author,
                           List<CrossrefContributor> editor,
                           List<CrossrefFunder> funder,
                           List<CrossrefLicense> license,
                           List<CrossrefReference> reference,
                           Map<String, List<CrossrefIdEntry>> relation,
                           CrossrefDate issued,
                           CrossrefDate created,
                           CrossrefDate deposited,
                           @JsonProperty("published-print") CrossrefDate publishedPrint,
                           @JsonProperty("published-online") CrossrefDate publishedOnline,
                           CrossrefDate accepted,
                           @JsonProperty("update-to") List<CrossrefUpdateTo> updateTo,
                           String award,
                           List<CrossrefProject> project,
                           CrossrefResource resource) {
}
