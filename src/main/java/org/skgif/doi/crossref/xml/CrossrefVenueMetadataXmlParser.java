package org.skgif.doi.crossref.xml;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
            Document document = parseDocument(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node containerNode = (Node) xpath.evaluate(
                    "//*[local-name()='book' or local-name()='conference']"
                            + "/*[local-name()='book_series_metadata' or local-name()='book_metadata'"
                            + " or local-name()='proceedings_series_metadata' or local-name()='proceedings_metadata']",
                    document, XPathConstants.NODE);
            if (containerNode == null) {
                return Optional.empty();
            }

            // Book-shaped titles live in a titles/title wrapper; proceedings-shaped titles are a
            // flat proceedings_title element - try the wrapper first, then the flat element.
            String containerTitle = text(xpath, containerNode, "*[local-name()='titles']/*[local-name()='title']");
            if (containerTitle == null) {
                containerTitle = text(xpath, containerNode, "*[local-name()='proceedings_title']");
            }
            if (containerTitle == null) {
                // Nothing usable to enrich the venue with - caller falls back to the REST JSON.
                return Optional.empty();
            }
            String containerDoi = text(xpath, containerNode, "*[local-name()='doi_data']/*[local-name()='doi']");

            Node seriesMetadata =
                    (Node) xpath.evaluate("*[local-name()='series_metadata']", containerNode, XPathConstants.NODE);
            String seriesTitle = seriesMetadata == null ? null
                    : text(xpath, seriesMetadata, "*[local-name()='titles']/*[local-name()='title']");
            List<String> seriesIssns =
                    seriesMetadata == null ? List.of() : textList(xpath, seriesMetadata, "*[local-name()='issn']");
            String volume = text(xpath, containerNode, "*[local-name()='volume']");

            List<String> isbns = textList(xpath, containerNode, "*[local-name()='isbn']");
            String publisherName =
                    text(xpath, containerNode, "*[local-name()='publisher']/*[local-name()='publisher_name']");
            String publisherPlace =
                    text(xpath, containerNode, "*[local-name()='publisher']/*[local-name()='publisher_place']");

            return Optional.of(new CrossrefVenueMetadata(containerTitle, containerDoi, seriesTitle, seriesIssns,
                    volume, isbns, publisherName, publisherPlace));
        } catch (Exception e) {
            // Malformed/unexpected XML shape degrades to the REST-JSON-only venue - never worth
            // failing the whole product response over an enrichment call, including from an
            // unexpected runtime error (e.g. a null deref on a surprising document shape).
            return Optional.empty();
        }
    }

    private static Document parseDocument(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE hardening - this parses content fetched live from the network.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static String text(XPath xpath, Node context, String expression) throws XPathExpressionException {
        String value = (String) xpath.evaluate(expression, context, XPathConstants.STRING);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> textList(XPath xpath, Node context, String expression)
            throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, context, XPathConstants.NODESET);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }
}
