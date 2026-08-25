package org.skgif.doi.datacite.mapper;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
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

    /** Pattern matching a bare DOI's {@code 10.<4-9 digits>/<suffix>} shape. */
    private static final Pattern DOI_SHAPE = Pattern.compile("10\\.\\d{4,9}/.+");

    /**
     * The {@code funderIdentifierType} values documented in the DataCite Metadata Schema
     * (https://datacite-metadata-schema.readthedocs.io/en/4.7/properties/fundingreference/#a-funderidentifiertype).
     * DataCite has added values to this list before and may do so again - {@link
     * #fromValue(String)} returns {@link Optional#empty()} rather than throwing for a value not
     * yet in this enum, so an unrecognized type is treated the same as any other non-ROR type
     * instead of failing the mapping.
     */
    private enum DataCiteFunderIdentifierType {

        CROSSREF_FUNDER_ID("Crossref Funder ID"),
        GRID("GRID"),
        ISNI("ISNI"),
        ROR("ROR"),
        OTHER("Other");

        /** Reverse lookup from {@link #value()} back to the enum constant. */
        private static final Map<String, DataCiteFunderIdentifierType> BY_VALUE = Arrays.stream(values())
                .collect(toMap(DataCiteFunderIdentifierType::value, identity()));

        /** The constant's underlying DataCite {@code funderIdentifierType} value. */
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private final String value;

        DataCiteFunderIdentifierType(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        static Optional<DataCiteFunderIdentifierType> fromValue(@Nullable String value) {
            return Optional.ofNullable(BY_VALUE.get(value));
        }
    }

    /** Resolves a funder's DOI-shaped identifier to a real local_identifier. */
    private final LocalIdentifiers localIdentifiers;

    DataCiteFundingMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    List<ProductAllOfFunding> funding(DataCiteAttributes attributes) {
        List<DataCiteFundingReference> fundingReferences = attributes.fundingReferences();
        if (fundingReferences == null) {
            return List.of();
        }
        return fundingReferences.stream()
                .<ProductAllOfFunding>map(fundingReference -> {
                    // DataCite funding references carry no stable identifier for the grant
                    // itself (unlike the funder, which often has a ROR) - the award
                    // number/title is the closest thing to a natural key, so that's what the
                    // otf id is built from.
                    String label = fundingReference.awardNumber() != null ?
                            fundingReference.awardNumber() :
                            fundingReference.awardTitle();
                    return new GrantLite()
                            .localIdentifier(MapperTextUtils.otf(attributes.doi(), label))
                            .entityType(GrantLite.EntityTypeEnum.GRANT)
                            .grantNumber(fundingReference.awardNumber())
                            .titles(fundingReference.awardTitle() != null ?
                                    Map.of("en", fundingReference.awardTitle()) : null)
                            .fundingAgency(fundingAgency(attributes.doi(), fundingReference).orElse(null));
                })
                .toList();
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
     * @return the mapped Organisation, or Optional.empty() if fundingReference has no funder name
     */
    private Optional<Organisation> fundingAgency(@Nullable String doi, DataCiteFundingReference fundingReference) {
        if (fundingReference.funderName() == null) {
            return Optional.empty();
        }
        // Held in a local rather than re-read via the accessor so the null check is directly
        // visible to the nullness checker at the stripRorUrl call below, which takes a @NonNull
        // value.
        String funderIdentifier = fundingReference.funderIdentifier();
        boolean hasRor = funderIdentifier != null &&
                DataCiteFunderIdentifierType.fromValue(fundingReference.funderIdentifierType())
                        .filter(type -> type == DataCiteFunderIdentifierType.ROR)
                        .isPresent();
        String bareRor = funderIdentifier != null && hasRor ?
                ExternalIdentifierUrls.stripRorUrl(funderIdentifier) : null;
        String funderDoi = hasRor ? null : extractDoi(funderIdentifier).orElse(null);
        String doiLocalIdentifier = funderDoi != null ? localIdentifiers.toFullLocalIdentifier(funderDoi) : null;
        return Optional.of(
                EntityRefs.organisationRef(doi, fundingReference.funderName(), bareRor, doiLocalIdentifier,
                        funderDoi));
    }

    /**
     * Strips a {@code https://doi.org/}/{@code http://doi.org/} prefix if present, then checks
     * the remainder against the DOI shape ({@code 10.<4-9 digits>/<suffix>}) - returns the bare
     * DOI, or {@code null} if the identifier isn't DOI-shaped at all.
     *
     * @param identifier the raw identifier value to check, or null
     * @return the bare DOI, or Optional.empty() if identifier is null or not DOI-shaped
     */
    private Optional<String> extractDoi(@Nullable String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        String candidate = ExternalIdentifierUrls.stripDoiUrl(identifier);
        return Optional.ofNullable(DOI_SHAPE.matcher(candidate).matches() ? candidate : null);
    }
}
