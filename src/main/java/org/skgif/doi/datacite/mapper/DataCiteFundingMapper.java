package org.skgif.doi.datacite.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteFundingReference;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.ProductAllOfFunding;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;
import org.skgif.doi.util.LocalIdentifiers;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a DataCite record's {@code fundingReferences[]} onto {@code Product.funding}. Split out
 * of {@code DataCiteToSkgIfMapper} to keep that class down to orchestration. Needs {@link
 * LocalIdentifiers} to resolve a funder's DOI-shaped identifier into a real local_identifier, so -
 * unlike the purely-static title/contribution helpers - this is an instance collaborator,
 * constructed once by the facade.
 */
final class DataCiteFundingMapper {

    private static final Pattern DOI_SHAPE = Pattern.compile("10\\.\\d{4,9}/.+");
    private static final String SCHEME_ROR_UPPER = "ROR";

    private final LocalIdentifiers localIdentifiers;

    DataCiteFundingMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    List<ProductAllOfFunding> funding(DataCiteAttributes attributes) {
        if (attributes.fundingReferences() == null || attributes.fundingReferences().isEmpty()) {
            return List.of();
        }
        List<ProductAllOfFunding> result = new ArrayList<>();
        for (DataCiteFundingReference fundingReference : attributes.fundingReferences()) {
            // DataCite funding references carry no stable identifier for the grant itself
            // (unlike the funder, which often has a ROR) - the award number/title is the
            // closest thing to a natural key, so that's what the otf id is built from.
            String label = fundingReference.awardNumber() != null ?
                    fundingReference.awardNumber() :
                    fundingReference.awardTitle();
            GrantLite grant = new GrantLite()
                    .localIdentifier(MapperTextUtils.otf(attributes.doi(), label))
                    .entityType(GrantLite.EntityTypeEnum.GRANT)
                    .grantNumber(fundingReference.awardNumber())
                    .titles(fundingReference.awardTitle() != null ? Map.of("en", fundingReference.awardTitle()) : null)
                    .fundingAgency(fundingAgency(attributes.doi(), fundingReference));
            result.add(grant);
        }
        return result;
    }

    /**
     * DataCite's {@code funderIdentifierType} controlled vocabulary has no literal {@code "DOI"}
     * value, but {@code "Crossref Funder ID"} (and occasionally other/unlabeled identifiers) are
     * themselves Funder Registry DOIs in practice - functionally identical to how Crossref's own
     * mapper treats {@code funder[].DOI}. Rather than special-casing that one type label, this
     * detects the DOI shape directly on the identifier value, so any DOI-shaped
     * funderIdentifier is used regardless of what (if anything) its type claims to be - verified
     * live against a real "Crossref Funder ID"-typed record (see
     * {@code datacite-thesis-crossref-funder-id-4342.json}). Non-DOI identifier types (GRID,
     * ISNI, Wikidata) still have no home here and fall back to an otf id.
     *
     * @param doi              the owning record's DOI, used to build a deterministic otf id when the funder
     *                         has neither a ROR nor a DOI-shaped identifier
     * @param fundingReference the DataCite funding reference to derive a funding agency from
     * @return the mapped Organisation, or null if fundingReference has no funder name
     */
    private Organisation fundingAgency(String doi, DataCiteFundingReference fundingReference) {
        if (fundingReference.funderName() == null) {
            return null;
        }
        boolean hasRor = fundingReference.funderIdentifier() != null &&
                SCHEME_ROR_UPPER.equalsIgnoreCase(fundingReference.funderIdentifierType());
        String bareRor = hasRor ? MapperTextUtils.stripRorUrl(fundingReference.funderIdentifier()) : null;
        String funderDoi = hasRor ? null : extractDoi(fundingReference.funderIdentifier());
        String doiLocalIdentifier = funderDoi != null ? localIdentifiers.toFullLocalIdentifier(funderDoi) : null;
        return EntityRefs.organisationRef(doi, fundingReference.funderName(), bareRor, doiLocalIdentifier, funderDoi);
    }

    /**
     * Strips a {@code https://doi.org/}/{@code http://doi.org/} prefix if present, then checks
     * the remainder against the DOI shape ({@code 10.<4-9 digits>/<suffix>}) - returns the bare
     * DOI, or {@code null} if the identifier isn't DOI-shaped at all.
     *
     * @param identifier the raw identifier value to check, or null
     * @return the bare DOI, or null if identifier is null or not DOI-shaped
     */
    private String extractDoi(String identifier) {
        if (identifier == null) {
            return null;
        }
        String candidate = identifier;
        if (candidate.startsWith(ExternalIdentifierUrls.DOI_BASE_URL)) {
            candidate = candidate.substring(ExternalIdentifierUrls.DOI_BASE_URL.length());
        } else if (candidate.startsWith(ExternalIdentifierUrls.DOI_HTTP_BASE_URL)) {
            candidate = candidate.substring(ExternalIdentifierUrls.DOI_HTTP_BASE_URL.length());
        }
        return DOI_SHAPE.matcher(candidate).matches() ? candidate : null;
    }
}
