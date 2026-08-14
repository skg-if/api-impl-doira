package org.skgif.doi.medra.dto;

import java.util.List;

/**
 * A mEDRA ONIX-for-DOI record, flattened out of the raw XML by {@code MedraOnixXmlParser} into a
 * form {@code MedraToSkgIfMapper} can map declaratively (field access only, no XPath) - same
 * division of labor as {@code CrossrefWork} (Jackson JSON DTO) has from {@code
 * CrossrefToSkgIfMapper}, just built by a hand-written XML parser instead of Jackson.
 *
 * <p>{@code titles} are the {@code ContentItem} (article) level titles only - the journal/series
 * title lives separately in {@code journalTitle}, since ONIX uses the identical {@code
 * Title}/{@code TitleType} shape at both levels and only the ancestor element tells them apart.
 * {@code issns} come from the journal/series level ({@code SerialWork}/{@code SerialVersion}),
 * never from the article itself - ONIX-for-DOI gives the article no identifier of its own besides
 * its DOI.
 *
 * <p>{@code workElementName} is the local name of the element that directly wraps {@code DOI},
 * {@code SerialPublication}, {@code JournalIssue}, and {@code ContentItem} - {@code
 * DOISerialArticleWork} or {@code DOISerialArticleVersion}, depending on which of mEDRA's two
 * root-message variants registered the record (see {@code MedraOnixXmlParser}). Read straight
 * off the document rather than hardcoded, so a schema variant this parser hasn't been taught
 * about still produces a truthful (if unfamiliar) label instead of a guessed-at one.
 */
public record MedraWork(
        String doi,
        List<MedraTitle> titles,
        List<MedraContributor> contributors,
        String abstractText,
        String publicationDate,
        String journalTitle,
        List<String> issns,
        String registrantName,
        String publisherName,
        String workElementName) {
}
