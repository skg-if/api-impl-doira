package org.skgif.doi.medra.dto;

/**
 * One ONIX-for-DOI {@code <Title>} element ({@code TitleType}/{@code TitleText}, {@code language}
 * as an XML attribute) - the same flat shape is used at both {@code ContentItem} (article) and
 * {@code SerialWork} (journal/series) level, so this record alone doesn't say which; the parser
 * keeps the two separate by the ancestor it queried under (see {@code MedraOnixXmlParser}).
 *
 * @param titleType the ONIX TitleType code
 * @param language  the title's language (XML attribute)
 * @param text      the title text
 */
public record MedraTitle(
        String titleType,
        String language,
        String text) {
}
