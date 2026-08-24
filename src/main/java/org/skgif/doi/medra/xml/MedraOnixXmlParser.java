package org.skgif.doi.medra.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.skgif.doi.medra.dto.MedraContributor;
import org.skgif.doi.medra.dto.MedraTitle;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.util.XmlParsingUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses mEDRA's ONIX-for-DOI 2.0 XML (as served by {@code https://api.medra.org/metadata/{doi}})
 * into a {@link MedraWork}. Unlike Crossref's XML "transform" (an enrichment fragment on top of a
 * JSON primary record - see {@code CrossrefVenueMetadataXmlParser}), this XML *is* the entire
 * record, so a parse failure here has no lesser representation to fall back to; the caller treats
 * an empty {@link Optional} as not-found.
 *
 * <p>mEDRA serves at least two root-message variants for the same underlying "serial article"
 * schema - {@code ONIXDOISerialArticleWorkRegistrationMessage} and {@code
 * ...VersionRegistrationMessage} - confirmed live to differ only in element naming ({@code
 * DOISerialArticleWork} vs {@code DOISerialArticleVersion}), not in the nesting of the fields
 * this parser cares about (both put {@code DOI}, {@code SerialPublication}, {@code JournalIssue},
 * and {@code ContentItem} as direct children of that wrapper). Every lookup below uses {@code
 * local-name()}-based XPath (same style as {@code CrossrefVenueMetadataXmlParser}) so it doesn't
 * need to distinguish the two, and would also tolerate other ONIX-DOI schema families this parser
 * hasn't been taught about - it simply won't find a {@code ContentItem} and degrade to {@code
 * Optional.empty()}.
 */
public final class MedraOnixXmlParser {

    /** ONIX {@code TitleType} code for the record's full/main title. */
    private static final String TITLE_TYPE_FULL = "01";

    private MedraOnixXmlParser() {
    }

    /**
     * Parses a mEDRA ONIX-for-DOI document into the subset of fields this API maps.
     *
     * @param xml the raw mEDRA ONIX-for-DOI XML document, or null
     * @return the parsed work, or empty if xml is null/blank or no {@code ContentItem} is found
     */
    public static Optional<MedraWork> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        try {
            Document document = XmlParsingUtils.parseDocument(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();

            String doi = XmlParsingUtils.text(xpath, document, "//*[local-name()='DOI']").orElse(null);
            if (doi == null) {
                return Optional.empty();
            }
            // The element that directly wraps DOI (DOISerialArticleWork/...Version) - read via
            // local-name() rather than hardcoding either variant's name, so an unfamiliar future
            // one still produces a truthful label (see MedraWork#workElementName's javadoc).
            String workElementName =
                    XmlParsingUtils.text(xpath, document, "local-name(//*[local-name()='DOI']/parent::*)").orElse(null);

            Node contentItem =
                    (Node) xpath.evaluate("//*[local-name()='ContentItem']", document, XPathConstants.NODE);
            if (contentItem == null) {
                return Optional.empty();
            }

            String abstractText = XmlParsingUtils.text(xpath, contentItem,
                    "*[local-name()='OtherText'][*[local-name()='TextTypeCode']='01']/*[local-name()='Text']")
                    .orElse(null);
            String publicationDate = XmlParsingUtils.text(xpath, contentItem, "*[local-name()='PublicationDate']")
                    .orElse(null);

            Node serialWork =
                    (Node) xpath.evaluate("//*[local-name()='SerialWork']", document, XPathConstants.NODE);
            String journalTitle = serialWork == null ? null : journalTitle(xpath, serialWork);
            String publisherName = serialWork == null ? null :
                    XmlParsingUtils.text(xpath, serialWork,
                            "*[local-name()='Publisher']/*[local-name()='PublisherName']")
                            .orElse(null);
            String registrantName = XmlParsingUtils.text(xpath, document, "//*[local-name()='RegistrantName']").orElse(
                    null);

            List<String> issns = XmlParsingUtils.textList(xpath, document,
                    "//*[local-name()='SerialVersion']/*[local-name()='ProductIdentifier']" +
                            "[*[local-name()='ProductIDType']='07']/*[local-name()='IDValue']")
                    .stream().distinct().toList();

            return Optional.of(new MedraWork(doi, titles(xpath, contentItem), contributors(xpath, contentItem),
                    abstractText, publicationDate, journalTitle, issns, registrantName, publisherName,
                    workElementName));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    /**
     * {@code ContentItem}'s own titles - distinct from {@link #journalTitle}.
     *
     * @param xpath       the XPath instance to evaluate queries with
     * @param contentItem the {@code ContentItem} node to read titles from
     * @return the ContentItem's titles, in document order
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private static List<MedraTitle> titles(XPath xpath, Node contentItem) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate("*[local-name()='Title']", contentItem, XPathConstants.NODESET);
        List<MedraTitle> titles = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node titleNode = nodes.item(i);
            String text = XmlParsingUtils.text(xpath, titleNode, "*[local-name()='TitleText']").orElse(null);
            if (text == null) {
                continue;
            }
            titles.add(new MedraTitle(XmlParsingUtils.text(xpath, titleNode, "*[local-name()='TitleType']").orElse(
                    null),
                    attribute(titleNode, "language").orElse(null), text));
        }
        return titles;
    }

    /**
     * The journal/series' own name, for the Venue - not part of {@code Product.titles}. A record
     * can carry several ({@code TitleType}, {@code language}) combinations at this level (e.g.
     * full title and abbreviated/key title, in more than one language); this picks the first
     * {@code TitleType 01} (full title) entry in document order, falling back to whatever title
     * is first if none is typed {@code 01} - a single-string Venue name has no room to keep every
     * combination, so anything past the first is dropped (see the "titles" row in
     * SKG_IF_DOI_MAPPING_PRODUCT.md for the AAPP fixture that exercises this).
     *
     * @param xpath      the XPath instance to evaluate queries with
     * @param serialWork the {@code SerialWork} node to read the journal/series title from
     * @return the journal/series' own name, or null if none found
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private static String journalTitle(XPath xpath, Node serialWork) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate("*[local-name()='Title']", serialWork, XPathConstants.NODESET);
        String firstAny = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node titleNode = nodes.item(i);
            String text = XmlParsingUtils.text(xpath, titleNode, "*[local-name()='TitleText']").orElse(null);
            if (text == null) {
                continue;
            }
            if (firstAny == null) {
                firstAny = text;
            }
            if (TITLE_TYPE_FULL.equals(XmlParsingUtils.text(xpath, titleNode, "*[local-name()='TitleType']").orElse(
                    null))) {
                return text;
            }
        }
        return firstAny;
    }

    private static List<MedraContributor> contributors(XPath xpath, Node contentItem) throws XPathExpressionException {
        NodeList nodes =
                (NodeList) xpath.evaluate("*[local-name()='Contributor']", contentItem, XPathConstants.NODESET);
        List<MedraContributor> contributors = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node contributorNode = nodes.item(i);
            contributors.add(new MedraContributor(
                    XmlParsingUtils.text(xpath, contributorNode, "*[local-name()='ContributorRole']").orElse(null),
                    XmlParsingUtils.text(xpath, contributorNode, "*[local-name()='NamesBeforeKey']").orElse(null),
                    XmlParsingUtils.text(xpath, contributorNode, "*[local-name()='KeyNames']").orElse(null),
                    XmlParsingUtils.text(xpath, contributorNode, "*[local-name()='PersonName']").orElse(null),
                    XmlParsingUtils.text(xpath, contributorNode, "*[local-name()='PersonNameInverted']").orElse(null)));
        }
        return contributors;
    }

    private static Optional<String> attribute(Node node, String name) {
        if (node.getAttributes() == null) {
            return Optional.empty();
        }
        Node attr = node.getAttributes().getNamedItem(name);
        return Optional.ofNullable(attr == null || attr.getNodeValue().isBlank() ? null : attr.getNodeValue().trim());
    }
}
