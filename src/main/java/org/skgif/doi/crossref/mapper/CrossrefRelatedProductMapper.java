package org.skgif.doi.crossref.mapper;

import java.util.ArrayList;
import java.util.List;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefReference;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.EntityIdentifiersInner;
import org.skgif.doi.generated.model.ProductsRelated;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.LocalIdentifiers;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref work record's {@code reference[]}/{@code relation} fields onto {@code
 * Product.relatedProducts}. Split out of {@code CrossrefToSkgIfMapper} to keep that class down to
 * orchestration. Needs {@link LocalIdentifiers} to build a real local_identifier for DOI-bearing
 * references, so - unlike the purely-static title/contribution helpers - this is an instance
 * collaborator, constructed once by the facade.
 */
final class CrossrefRelatedProductMapper {

    private static final String SCHEME_DOI = "doi";

    private final LocalIdentifiers localIdentifiers;

    CrossrefRelatedProductMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    /**
     * Unlike DataCite (where citations live in {@code relatedIdentifiers[relationType=Cites]}),
     * Crossref's citation list is {@code reference[]} - verified live that plain works commonly
     * carry an empty {@code relation} map even with a populated {@code reference[]}, so that
     * hashmap is not a reliable source of "this work cites DOI X" and isn't used for {@code
     * cites} here. Only entries the depositing publisher asserted a DOI for get a real
     * identifier; free-text-only references still get an entry with an otf id (same fallback
     * DataCite's {@code relatedByType} uses for non-DOI related identifiers) rather than being
     * dropped.
     *
     * <p>{@code is-supplemented-by}, by contrast, is a distinct controlled-vocabulary
     * {@code relation} key that Crossref documents explicitly (see
     * <a href="https://www.crossref.org/documentation/schema-library/markup-guide-metadata-segments/relationships/">
     * Crossref's relationships markup guide</a>) and reliably populates when a publisher asserts
     * it - so unlike citations, it's read directly from {@code relation} rather than {@code
     * reference[]}.
     *
     * @param work the Crossref work record to derive related products from
     * @return the mapped related products (cites/isSupplementedBy), or null if there are none
     */
    ProductsRelated relatedProducts(CrossrefWork work) {
        List<ProductsRelatedCitesInner> cites = new ArrayList<>();
        if (work.reference() != null) {
            for (CrossrefReference reference : work.reference()) {
                if (reference.doi() != null) {
                    // Full https://doi.org/... URL, consistent with how this API identifies its
                    // own products and every other DOI-identified entity.
                    cites.add(new ProductsRelatedItem()
                            .localIdentifier(localIdentifiers.toFullLocalIdentifier(reference.doi()))
                            .entityType(EntityTypes.PRODUCT)
                            .identifiers(
                                    List.of(new EntityIdentifiersInner().scheme(SCHEME_DOI).value(reference.doi()))));
                    continue;
                }
                String label = reference.unstructured() != null ? reference.unstructured() : reference.key();
                cites.add(new ProductsRelatedItem()
                        .localIdentifier(MapperTextUtils.otf(work.doi(), label))
                        .entityType(EntityTypes.PRODUCT));
            }
        }
        List<ProductsRelatedCitesInner> isSupplementedBy = relatedByRelationType(work, "is-supplemented-by");
        if (cites.isEmpty() && isSupplementedBy.isEmpty()) {
            return null;
        }
        ProductsRelated related = new ProductsRelated();
        if (!cites.isEmpty()) {
            related.cites(cites);
        }
        if (!isSupplementedBy.isEmpty()) {
            related.isSupplementedBy(isSupplementedBy);
        }
        return related;
    }

    /**
     * Entries under {@code work.relation.get(relationType)} - DOI-shaped entries (Crossref's
     * {@code id-type: "doi"}) become a real, full-URL identifier just like a DOI-bearing {@code
     * reference[]} entry; anything else falls back to an otf id built from the raw {@code id}.
     *
     * @param work the Crossref work record to read {@code relation} from
     * @param relationType the relation key to read (e.g. "is-supplemented-by")
     * @return the mapped related-product entries for relationType, or empty if none/absent
     */
    private List<ProductsRelatedCitesInner> relatedByRelationType(CrossrefWork work, String relationType) {
        List<ProductsRelatedCitesInner> result = new ArrayList<>();
        if (work.relation() == null) {
            return result;
        }
        List<CrossrefIdEntry> entries = work.relation().get(relationType);
        if (entries == null) {
            return result;
        }
        for (CrossrefIdEntry entry : entries) {
            if (entry.id() == null) {
                continue;
            }
            if (SCHEME_DOI.equalsIgnoreCase(entry.idType())) {
                result.add(new ProductsRelatedItem()
                        .localIdentifier(localIdentifiers.toFullLocalIdentifier(entry.id()))
                        .entityType(EntityTypes.PRODUCT)
                        .identifiers(List.of(new EntityIdentifiersInner().scheme(SCHEME_DOI).value(entry.id()))));
                continue;
            }
            result.add(new ProductsRelatedItem()
                    .localIdentifier(MapperTextUtils.otf(work.doi(), entry.id()))
                    .entityType(EntityTypes.PRODUCT));
        }
        return result;
    }
}
