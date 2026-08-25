package org.skgif.doi.crossref.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;

class CrossrefToSkgIfMapperRelatedProductsTest extends CrossrefToSkgIfMapperTestBase {

    @Test
    void mapsRelatedProductsFromReferenceListDois() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertThat(product.getRelatedProducts().getCites()).isNotEmpty();
        assertThat(product.getRelatedProducts().getCites())
                .anyMatch(c -> "https://doi.org/10.1038/nature03509"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
    }

    @Test
    void mapsReferenceWithoutDoiToOtfIdInsteadOfExcludingIt() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        // This fixture's reference[] has 30 entries, one of which (key BFnature12373_CR17)
        // carries no DOI - it must still surface as a cites entry, via an otf id, not be dropped.
        final int expectedReferenceCount = 30;
        assertThat(product.getRelatedProducts().getCites()).hasSize(expectedReferenceCount);
        assertThat(product.getRelatedProducts().getCites())
                .anyMatch(c -> ((ProductsRelatedItem) c).getLocalIdentifier().startsWith("otf___"));
    }

    // crossref-journal-article-with-is-supplemented-by.json: a real IUCrData article (DOI
    // 10.1107/s2414314618016334) whose relation map carries 4 real is-supplemented-by entries
    // (all DOI-shaped supplement-file identifiers), alongside a normal reference[] - the first
    // fixture to exercise related_products.is_supplemented_by for Crossref.

    @Test
    void mapsIsSupplementedByFromRelationMap() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-is-supplemented-by.json");

        List<ProductsRelatedCitesInner> isSupplementedBy = product.getRelatedProducts().getIsSupplementedBy();
        final int expectedIsSupplementedByCount = 4;
        assertThat(isSupplementedBy).hasSize(expectedIsSupplementedByCount);
        ProductsRelatedItem first = (ProductsRelatedItem) isSupplementedBy.getFirst();
        assertThat(first.getLocalIdentifier()).isEqualTo("https://doi.org/10.1107/S2414314618016334/lh4040sup1.cif");
        assertThat(first.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(first.getIdentifiers().getFirst().getValue()).isEqualTo("10.1107/S2414314618016334/lh4040sup1.cif");

        // Same fixture also has a normal reference[] - adding is_supplemented_by must not
        // disturb cites.
        assertThat(product.getRelatedProducts().getCites()).isNotEmpty();
    }

    @Test
    void mapsIsSupplementedByToOtfIdWhenRelationEntryIsNotDoiShaped() throws IOException {
        // No real fixture is expected to carry a non-DOI is-supplemented-by entry - mutated in
        // Java from the real fixture's own DOI-shaped entry to prove the otf fallback still works.
        CrossrefWork work = readFixture("crossref-journal-article-with-is-supplemented-by.json");
        List<CrossrefIdEntry> supplements =
                Objects.requireNonNull(Objects.requireNonNull(work.relation()).get("is-supplemented-by"));
        CrossrefIdEntry original = supplements.getFirst();
        supplements.set(0, new CrossrefIdEntry(original.id(), "handle", original.assertedBy()));

        Product product = mapper.toProduct(work);

        ProductsRelatedItem item = (ProductsRelatedItem) product.getRelatedProducts().getIsSupplementedBy().getFirst();
        assertThat(item.getLocalIdentifier()).startsWith("otf___");
    }

    @Test
    void mapsReferenceWithNeitherDoiNorUnstructuredToOtfIdFromKey() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        // reference key "ref3" carries no DOI and no unstructured text - the otf id must
        // fall back to the reference key itself rather than being dropped.
        final int expectedCitesCount = 5;
        assertThat(product.getRelatedProducts().getCites()).hasSize(expectedCitesCount);
        assertThat(product.getRelatedProducts().getCites())
                .anyMatch(c -> "otf___10-17537-icmbb18-42___ref3"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
    }
}
