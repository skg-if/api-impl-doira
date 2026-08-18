package org.skgif.doi.datacite.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DataCiteToSkgIfMapperRelatedProductsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataCiteToSkgIfMapper mapper = new DataCiteToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data().attributes();
        }
    }

    // datacite-zenodo-editor-21232199.json: a real Zenodo journal-article deposit. It has
    // relatedIdentifiers of types the mapper doesn't model ("HasVersion", "IsPartOf") and no
    // "Cites"/"IsCitedBy" - related_products must stay unset rather than surfacing either of them.

    @Test
    void surfacesIsPartOfButNotUnmodeledHasVersion() throws IOException {
        // relatedIdentifiers has a "HasVersion" DOI (unmodeled - stays out entirely) and an
        // "IsPartOf" ISSN, which now does surface as related_products.is_part_of.
        Product product = mapFixture("datacite-zenodo-editor-21232199.json");

        assertNull(product.getRelatedProducts().getCites());
        assertEquals(1, product.getRelatedProducts().getIsPartOf().size());
        ProductsRelatedItem isPartOf = (ProductsRelatedItem) product.getRelatedProducts().getIsPartOf().getFirst();
        assertEquals("issn", isPartOf.getIdentifiers().getFirst().getScheme());
        assertEquals("2230-9578", isPartOf.getIdentifiers().getFirst().getValue());
    }

    // datacite-zenodo-cites-references-21914195.json: a real Zenodo deposit (DOI
    // 10.5281/zenodo.21914195) whose relatedIdentifiers mix DataCite's two citation-like
    // relation types - "Cites" and "References" - alongside "IsPartOf"/"IsDocumentedBy" (now
    // modeled too, into their own fields - see the zenodo.21827103 block below) and
    // "IsDerivedFrom"/"HasVersion" (still unmodeled). Both citation types must land in the
    // same related_products.cites array, since SKG-IF has no separate field for "References".

    @Test
    void mapsBothCitesAndReferencesRelationTypesIntoTheSameCitesArray() throws IOException {
        Product product = mapFixture("datacite-zenodo-cites-references-21914195.json");

        // 2 "Cites" entries + 1 "References" entry = 3 cites; "IsDerivedFrom"/"HasVersion"
        // (still unmodeled) must not add any more, and "IsPartOf"/"IsDocumentedBy" land in
        // their own fields rather than here.
        final int expectedCitesCount = 3;
        assertEquals(expectedCitesCount, product.getRelatedProducts().getCites().size());
        boolean hasReferencesEntry = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.5281/zenodo.21913675"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
        assertTrue(hasReferencesEntry);
    }

    @Test
    void mapsNonDoiRelatedIdentifierToOtfId() throws IOException {
        // The "Cites" entry with relatedIdentifierType "ISBN" (978-963-281-509-1) has no DOI,
        // so it must fall back to an otf id rather than being dropped or mis-typed as a DOI.
        Product product = mapFixture("datacite-zenodo-cites-references-21914195.json");

        ProductsRelatedItem isbnCite = (ProductsRelatedItem) product.getRelatedProducts().getCites().stream()
                .filter(c -> ((ProductsRelatedItem) c).getLocalIdentifier().startsWith("otf___"))
                .findFirst()
                .orElseThrow();
        assertEquals("isbn", isbnCite.getIdentifiers().getFirst().getScheme());
        assertEquals("978-963-281-509-1", isbnCite.getIdentifiers().getFirst().getValue());
    }

    // datacite-zenodo-relations-21827103.json: a real Zenodo dataset (DOI
    // 10.5281/zenodo.21827103) whose relatedIdentifiers exercise 4 relation types the mapper
    // didn't previously model - "IsSupplementedBy", "IsDocumentedBy", "IsNewVersionOf", and
    // "IsPartOf" - each landing in its own related_products field rather than "cites". It also
    // carries a decoy, "IsSupplementTo" (the inverse of "IsSupplementedBy", easy to confuse by
    // name), and "HasVersion", neither of which the mapper models - both must stay excluded.

    @Test
    void mapsIsSupplementedByIsDocumentedByAndIsNewVersionOf() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertEquals(1, related.getIsSupplementedBy().size());
        ProductsRelatedItem isSupplementedBy = (ProductsRelatedItem) related.getIsSupplementedBy().getFirst();
        assertEquals("url", isSupplementedBy.getIdentifiers().getFirst().getScheme());
        assertEquals("https://github.com/vicgos/MICRO", isSupplementedBy.getIdentifiers().getFirst().getValue());

        assertEquals(1, related.getIsDocumentedBy().size());
        assertEquals("handle",
                ((ProductsRelatedItem) related.getIsDocumentedBy().getFirst()).getIdentifiers().getFirst().getScheme());

        assertEquals(2, related.getIsNewVersionOf().size());
        boolean hasNsdVersion = related.getIsNewVersionOf().stream()
                .anyMatch(r -> "10.18712/NSD-NSD2457-V3"
                        .equals(((ProductsRelatedItem) r).getIdentifiers().getFirst().getValue()));
        assertTrue(hasNsdVersion);
    }

    @Test
    void mapsIsPartOfWithFullDoiLocalIdentifier() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertEquals(2, related.getIsPartOf().size());
        boolean hasKnownPart = related.getIsPartOf().stream()
                .anyMatch(r -> "https://doi.org/10.5281/zenodo.21827101"
                        .equals(((ProductsRelatedItem) r).getLocalIdentifier()));
        assertTrue(hasKnownPart);
    }

    @Test
    void doesNotConfuseIsSupplementToWithIsSupplementedByOrSurfaceHasVersion() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        // "IsSupplementTo" and "IsSupplementedBy" both target the same identifier (10852/56047)
        // in this fixture, so a substring/prefix mixup would silently double it into
        // is_supplemented_by - it must appear there exactly once, from "IsSupplementedBy" only.
        assertEquals(1, related.getIsSupplementedBy().size());
        // Neither "IsSupplementTo" nor "HasVersion" have a related_products field at all -
        // cites/citedBy stay null, not just empty.
        assertNull(related.getCites());
    }
}
