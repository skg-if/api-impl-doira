package org.skgif.doi.crossref.xml;

import static org.skgif.doi.util.SpotBugsSuppressions.EI_EXPOSE_REP;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Container-level metadata parsed from Crossref's XML "transform" representation of a
 * chapter-in-a-book or paper-in-proceedings record - see {@link CrossrefVenueMetadataXmlParser}.
 * "Container" covers both a book (chapter-in-a-book types) and a conference proceedings
 * (proceedings-article) - the two share an identical shape in Crossref's XML schema except for
 * how the container's own title is stored. Fields other than {@code containerTitle} may be
 * {@code null}/empty depending on whether the container is part of a series ({@code
 * seriesTitle}, {@code seriesIssns}, and {@code volume} are only ever present in that case) and
 * on whether Crossref recorded a DOI for the container itself ({@code containerDoi} - absent on
 * some real proceedings records).
 *
 * @param containerTitle the container's own title
 * @param containerDoi   the container's own DOI, or null if Crossref recorded none (common for
 *                       proceedings records)
 * @param seriesTitle    the series' own title, or null if the container is not part of a series
 * @param seriesIssns    the series' ISSNs, or empty if the container is not part of a series
 * @param volume         the series volume number, or null if the container is not part of a series
 * @param isbns          the container's ISBNs
 * @param publisherName  the container's publisher
 * @param publisherPlace the publisher's place
 */
@SuppressFBWarnings(value = EI_EXPOSE_REP, justification = "Plain XML-parsed data carrier with no independent " +
        "mutation path once constructed")
public record CrossrefVenueMetadata(
        @Nullable String containerTitle,
        @Nullable String containerDoi,
        @Nullable String seriesTitle,
        @Nullable List<String> seriesIssns,
        @Nullable String volume,
        @Nullable List<String> isbns,
        @Nullable String publisherName,
        @Nullable String publisherPlace) {
}
