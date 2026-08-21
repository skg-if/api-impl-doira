package org.skgif.doi.crossref.xml;

import java.util.List;
import java.util.Optional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.skgif.doi.util.XmlParsingUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Parses Crossref's XML "transform" representation ({@code application/vnd.crossref.unixsd+xml})
 * of a chapter-in-a-book or paper-in-proceedings record, extracting the containing book/
 * proceedings' title/DOI/ISBN/publisher and, when it's part of a series, the series' own
 * title/ISSN/volume number - all considerably less ambiguous here than in the REST JSON's {@code
 * container-title[]} array (see {@code CrossrefBiblioMapper#venue}, which prefers this over
 * {@code container-title[0]} when present).
 *
 * <p>Crossref's schema puts this metadata under one of four mutually exclusive elements, two
 * possible pairs depending on whether the record is a book chapter or a proceedings paper:
 * <ul>
 * <li>{@code book_series_metadata} / {@code proceedings_series_metadata} - part of a series -
 * its *direct* title child (`titles/title` for books, `proceedings_title` for proceedings) is
 * the container's own title, distinct from the nested {@code series_metadata/titles/title}, the
 * series name.
 * <li>{@code book_metadata} / {@code proceedings_metadata} - standalone, no series - no
 * series/volume/series-ISSN concept at all.
 * </ul>
 * All four carry the container's own DOI at {@code doi_data/doi} when Crossref recorded one -
 * distinct from the chapter/paper's own DOI under {@code content_item/doi_data/doi} or {@code
 * conference_paper/doi_data/doi}, which is already known as the record's own {@code work.doi}.
 * Unlike book records (which always carried a container DOI in the fixtures tested so far), real
 * proceedings records commonly have none at all - callers must treat {@code containerDoi} as
 * optional and fall back to an otf id.
 */
public final class CrossrefVenueMetadataXmlParser {

    private CrossrefVenueMetadataXmlParser() {
    }

    /**
     * @param xml the raw Crossref XML transform document, or null
     * @return the parsed venue metadata, or empty if xml is null/blank or unparseable
     */
    public static Optional<CrossrefVenueMetadata> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        try {
            Document document = XmlParsingUtils.parseDocument(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node containerNode = (Node) xpath.evaluate(
                    "//*[local-name()='book' or local-name()='conference']" +
                            "/*[local-name()='book_series_metadata' or local-name()='book_metadata'" +
                            " or local-name()='proceedings_series_metadata' or local-name()='proceedings_metadata']",
                    document, XPathConstants.NODE);
            if (containerNode == null) {
                return Optional.empty();
            }

            // Book-shaped titles live in a titles/title wrapper; proceedings-shaped titles are a
            // flat proceedings_title element - try the wrapper first, then the flat element.
            String containerTitle =
                    XmlParsingUtils.text(xpath, containerNode, "*[local-name()='titles']/*[local-name()='title']")
                            .orElse(null);
            if (containerTitle == null) {
                containerTitle = XmlParsingUtils.text(xpath, containerNode, "*[local-name()='proceedings_title']")
                        .orElse(null);
            }
            if (containerTitle == null) {
                // Nothing usable to enrich the venue with - caller falls back to the REST JSON.
                return Optional.empty();
            }
            String containerDoi =
                    XmlParsingUtils.text(xpath, containerNode, "*[local-name()='doi_data']/*[local-name()='doi']")
                            .orElse(null);

            Node seriesMetadata =
                    (Node) xpath.evaluate("*[local-name()='series_metadata']", containerNode, XPathConstants.NODE);
            String seriesTitle = seriesMetadata == null ? null :
                    XmlParsingUtils.text(xpath, seriesMetadata, "*[local-name()='titles']/*[local-name()='title']")
                            .orElse(null);
            List<String> seriesIssns = seriesMetadata == null ? List.of() :
                    XmlParsingUtils.textList(xpath, seriesMetadata, "*[local-name()='issn']");
            String volume = XmlParsingUtils.text(xpath, containerNode, "*[local-name()='volume']").orElse(null);

            List<String> isbns = XmlParsingUtils.textList(xpath, containerNode, "*[local-name()='isbn']");
            String publisherName =
                    XmlParsingUtils.text(xpath, containerNode,
                            "*[local-name()='publisher']/*[local-name()='publisher_name']")
                            .orElse(null);
            String publisherPlace =
                    XmlParsingUtils.text(xpath, containerNode,
                            "*[local-name()='publisher']/*[local-name()='publisher_place']")
                            .orElse(null);

            return Optional.of(new CrossrefVenueMetadata(containerTitle, containerDoi, seriesTitle, seriesIssns,
                    volume, isbns, publisherName, publisherPlace));
        } catch (Exception e) {
            // Malformed/unexpected XML shape degrades to the REST-JSON-only venue - never worth
            // failing the whole product response over an enrichment call, including from an
            // unexpected runtime error (e.g. a null deref on a surprising document shape).
            return Optional.empty();
        }
    }

}
