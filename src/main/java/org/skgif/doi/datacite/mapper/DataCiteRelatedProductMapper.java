package org.skgif.doi.datacite.mapper;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.skgif.doi.util.SpotBugsSuppressions.IMPROPER_UNICODE;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteRelatedIdentifier;
import org.skgif.doi.generated.model.EntityIdentifiersInner;
import org.skgif.doi.generated.model.ProductsRelated;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.LocalIdentifiers;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a DataCite record's {@code relatedIdentifiers[]} onto {@code Product.relatedProducts}.
 * Split out of {@code DataCiteToSkgIfMapper} to keep that class down to orchestration. Needs
 * {@link LocalIdentifiers} to build a real local_identifier for DOI-identified related products,
 * so - unlike the purely-static title/contribution helpers - this is an instance collaborator,
 * constructed once by the facade.
 */
final class DataCiteRelatedProductMapper {

    /**
     * The {@code relatedIdentifierType} values documented in the DataCite Metadata Schema. DataCite
     * has added values to this list before and may do so again - {@link #fromValue(String)} returns
     * {@link Optional#empty()} rather than throwing for a value not yet in this enum, so a DataCite
     * record using a newer value still falls back to a lowercased pass-through scheme (see {@link
     * #relatedByType}) instead of failing the mapping.
     */
    private enum DataCiteRelatedIdentifierType {

        ARK("ARK"),
        ARXIV("arXiv"),
        BIBCODE("bibcode"),
        CSTR("CSTR"),
        DOI("DOI"),
        EAN13("EAN13"),
        EISSN("EISSN"),
        HANDLE("Handle"),
        IGSN("IGSN"),
        ISBN("ISBN"),
        ISSN("ISSN"),
        ISTC("ISTC"),
        LISSN("LISSN"),
        LSID("LSID"),
        PMID("PMID"),
        PURL("PURL"),
        RAID("RAiD"),
        RRID("RRID"),
        SWHID("SWHID"),
        UPC("UPC"),
        URL("URL"),
        URN("URN"),
        W3ID("w3id");

        /** Reverse lookup from {@link #value()} back to the enum constant. */
        private static final Map<String, DataCiteRelatedIdentifierType> BY_VALUE = Arrays.stream(values())
                .collect(toMap(DataCiteRelatedIdentifierType::value, identity()));

        /** The constant's underlying DataCite {@code relatedIdentifierType} value. */
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private final String value;

        DataCiteRelatedIdentifierType(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        static Optional<DataCiteRelatedIdentifierType> fromValue(@Nullable String value) {
            return Optional.ofNullable(BY_VALUE.get(value));
        }
    }

    /** Builds a real local_identifier for DOI-identified related products. */
    private final LocalIdentifiers localIdentifiers;

    DataCiteRelatedProductMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    Optional<ProductsRelated> relatedProducts(DataCiteAttributes attributes) {
        List<DataCiteRelatedIdentifier> relatedIdentifiers = attributes.relatedIdentifiers();
        if (relatedIdentifiers == null || relatedIdentifiers.isEmpty()) {
            return Optional.empty();
        }
        return buildRelated(attributes);
    }

    private Optional<ProductsRelated> buildRelated(DataCiteAttributes attributes) {
        // DataCite's controlled vocabulary has both "Cites" and "References" for what SKG-IF
        // models as a single relation - real records use either (verified live against
        // 10.5281/zenodo.21914195, which has 12 "Cites" entries plus one separate "References"
        // entry) - both feed the same output array.
        List<ProductsRelatedCitesInner> cites = new ArrayList<>();
        cites.addAll(relatedByType(attributes, "Cites"));
        cites.addAll(relatedByType(attributes, "References"));
        List<ProductsRelatedCitesInner> citedBy = relatedByType(attributes, "IsCitedBy");
        // "IsSupplementTo" is a distinct DataCite relation type from "IsSupplementedBy" (the
        // inverse direction) - verified live against 10.5281/zenodo.21827103, which carries
        // both an "IsSupplementedBy" and, on a different related identifier, an "IsSupplementTo"
        // that must NOT be picked up here.
        List<ProductsRelatedCitesInner> isSupplementedBy = relatedByType(attributes, "IsSupplementedBy");
        List<ProductsRelatedCitesInner> isDocumentedBy = relatedByType(attributes, "IsDocumentedBy");
        List<ProductsRelatedCitesInner> isNewVersionOf = relatedByType(attributes, "IsNewVersionOf");
        List<ProductsRelatedCitesInner> isPartOf = relatedByType(attributes, "IsPartOf");
        if (cites.isEmpty() && citedBy.isEmpty() && isSupplementedBy.isEmpty() && isDocumentedBy.isEmpty() &&
                isNewVersionOf.isEmpty() && isPartOf.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assembleRelated(cites, isSupplementedBy, isDocumentedBy, isNewVersionOf, isPartOf));
    }

    private ProductsRelated assembleRelated(List<ProductsRelatedCitesInner> cites,
            List<ProductsRelatedCitesInner> isSupplementedBy,
            List<ProductsRelatedCitesInner> isDocumentedBy,
            List<ProductsRelatedCitesInner> isNewVersionOf,
            List<ProductsRelatedCitesInner> isPartOf) {
        ProductsRelated related = new ProductsRelated();
        if (!cites.isEmpty()) {
            related.cites(cites);
        }
        if (!isSupplementedBy.isEmpty()) {
            related.isSupplementedBy(isSupplementedBy);
        }
        if (!isDocumentedBy.isEmpty()) {
            related.isDocumentedBy(isDocumentedBy);
        }
        if (!isNewVersionOf.isEmpty()) {
            related.isNewVersionOf(isNewVersionOf);
        }
        if (!isPartOf.isEmpty()) {
            related.isPartOf(isPartOf);
        }
        return related;
    }

    @SuppressFBWarnings(value = IMPROPER_UNICODE, justification = "type.value().toLowerCase(Locale.ROOT) is " +
            "flagged regardless of the explicit Locale argument - FindSecBugs's ImproperHandlingUnicodeDetector " +
            "matches toLowerCase by method name only, ignoring the descriptor - " + SPOTBUGS_REGISTER)
    private List<ProductsRelatedCitesInner> relatedByType(DataCiteAttributes attributes, String relationType) {
        List<ProductsRelatedCitesInner> result = new ArrayList<>();
        List<DataCiteRelatedIdentifier> relatedIdentifiers = attributes.relatedIdentifiers();
        if (relatedIdentifiers == null) {
            return result;
        }
        for (DataCiteRelatedIdentifier related : relatedIdentifiers) {
            if (!relationType.equals(related.relationType()) || related.relatedIdentifier() == null) {
                continue;
            }
            DataCiteRelatedIdentifierType type = DataCiteRelatedIdentifierType
                    .fromValue(related.relatedIdentifierType())
                    .orElse(null);
            String scheme = type != null ? type.value().toLowerCase(Locale.ROOT) :
                    unmappedScheme(related.relatedIdentifierType());
            // A related product with a DOI is identified by the full https://doi.org/... URL,
            // consistent with how this API identifies its own products; anything else falls
            // back to otf.
            String localIdentifier = type == DataCiteRelatedIdentifierType.DOI ?
                    localIdentifiers.toFullLocalIdentifier(related.relatedIdentifier()) :
                    MapperTextUtils.otf(attributes.doi(), related.relatedIdentifier());
            result.add(new ProductsRelatedItem()
                    .localIdentifier(localIdentifier)
                    .entityType(EntityTypes.PRODUCT.value())
                    .identifiers(List.of(new EntityIdentifiersInner()
                            .scheme(scheme)
                            .value(related.relatedIdentifier()))));
        }
        return result;
    }

    private static String unmappedScheme(@Nullable String relatedIdentifierType) {
        return relatedIdentifierType != null ? relatedIdentifierType.toLowerCase(Locale.ROOT) : "url";
    }
}
