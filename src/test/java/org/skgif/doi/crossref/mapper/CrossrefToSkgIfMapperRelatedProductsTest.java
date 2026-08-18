package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperRelatedProductsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message();
        }
    }

    @Test
    void mapsRelatedProductsFromReferenceListDois() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertFalse(product.getRelatedProducts().getCites().isEmpty());
        boolean hasKnownReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.1038/nature03509"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
        assertTrue(hasKnownReference);
    }

    @Test
    void mapsReferenceWithoutDoiToOtfIdInsteadOfExcludingIt() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        // This fixture's reference[] has 30 entries, one of which (key BFnature12373_CR17)
        // carries no DOI - it must still surface as a cites entry, via an otf id, not be dropped.
        final int expectedReferenceCount = 30;
        assertEquals(expectedReferenceCount, product.getRelatedProducts().getCites().size());
        boolean hasOtfReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> ((ProductsRelatedItem) c).getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfReference);
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
        assertEquals(expectedIsSupplementedByCount, isSupplementedBy.size());
        ProductsRelatedItem first = (ProductsRelatedItem) isSupplementedBy.getFirst();
        assertEquals("https://doi.org/10.1107/S2414314618016334/lh4040sup1.cif", first.getLocalIdentifier());
        assertEquals("doi", first.getIdentifiers().getFirst().getScheme());
        assertEquals("10.1107/S2414314618016334/lh4040sup1.cif", first.getIdentifiers().getFirst().getValue());

        // Same fixture also has a normal reference[] - adding is_supplemented_by must not
        // disturb cites.
        assertFalse(product.getRelatedProducts().getCites().isEmpty());
    }

    @Test
    void mapsIsSupplementedByToOtfIdWhenRelationEntryIsNotDoiShaped() throws IOException {
        // No real fixture is expected to carry a non-DOI is-supplemented-by entry - mutated in
        // Java from the real fixture's own DOI-shaped entry to prove the otf fallback still works.
        CrossrefWork work = readFixture("crossref-journal-article-with-is-supplemented-by.json");
        List<CrossrefIdEntry> supplements = work.relation().get("is-supplemented-by");
        CrossrefIdEntry original = supplements.getFirst();
        supplements.set(0, new CrossrefIdEntry(original.id(), "handle", original.assertedBy()));

        Product product = mapper.toProduct(work);

        ProductsRelatedItem item = (ProductsRelatedItem) product.getRelatedProducts().getIsSupplementedBy().getFirst();
        assertTrue(item.getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsReferenceWithNeitherDoiNorUnstructuredToOtfIdFromKey() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        // reference key "ref3" carries no DOI and no unstructured text - the otf id must
        // fall back to the reference key itself rather than being dropped.
        final int expectedCitesCount = 5;
        assertEquals(expectedCitesCount, product.getRelatedProducts().getCites().size());
        assertTrue(product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "otf___10-17537-icmbb18-42___ref3"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier())));
    }
}
