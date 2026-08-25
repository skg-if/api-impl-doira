package org.skgif.doi.util;

import static org.skgif.doi.util.SpotBugsError.Code.XPATH_INJECTION;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XXE-hardened XML document parsing and XPath text-extraction helpers, shared by {@code
 * CrossrefVenueMetadataXmlParser} and {@code MedraOnixXmlParser} - both parse XML fetched live
 * from a network call, using {@code local-name()}-based XPath so they don't need to distinguish
 * namespace variants.
 */
public final class XmlParsingUtils {

    private XmlParsingUtils() {
    }

    /**
     * Parses raw XML into a DOM document, hardened against XXE since this content comes off the network.
     *
     * @param xml the raw XML document to parse
     * @return the parsed document
     * @throws ParserConfigurationException if the underlying parser cannot be configured
     * @throws SAXException                 if the XML cannot be parsed
     * @throws IOException                  if the XML cannot be read
     */
    public static Document parseDocument(String xml) throws ParserConfigurationException, SAXException, IOException {
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

    /**
     * Evaluates an XPath expression to a single trimmed string value.
     *
     * @param xpath      the XPath instance to evaluate the expression with
     * @param context    the node to evaluate the expression relative to
     * @param expression the XPath string expression
     * @return the trimmed text value, or empty if null/blank
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    @SuppressFBWarnings(value = XPATH_INJECTION,
            justification = "Every caller across CrossrefVenueMetadataXmlParser and MedraOnixXmlParser passes a " +
                    "compile-time string literal, never a value built from parsed XML or other network-derived " +
                    "data - SpotBugs can't see across this shared utility's public method boundary to confirm that")
    public static Optional<String> text(XPath xpath, Node context, String expression) throws XPathExpressionException {
        String value = (String) xpath.evaluate(expression, context, XPathConstants.STRING);
        return Optional.ofNullable(value == null || value.isBlank() ? null : value.trim());
    }

    /**
     * Evaluates an XPath nodeset expression to the trimmed text of every matched node.
     *
     * @param xpath      the XPath instance to evaluate the expression with
     * @param context    the node to evaluate the expression relative to
     * @param expression the XPath nodeset expression
     * @return the trimmed, non-blank text content of each matched node, in document order
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    @SuppressFBWarnings(value = XPATH_INJECTION,
            justification = "Every caller across CrossrefVenueMetadataXmlParser and MedraOnixXmlParser passes a " +
                    "compile-time string literal, never a value built from parsed XML or other network-derived " +
                    "data - SpotBugs can't see across this shared utility's public method boundary to confirm that")
    public static List<String> textList(XPath xpath, Node context, String expression) throws XPathExpressionException {
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
