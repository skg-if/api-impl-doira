package org.skgif.doi.datacite.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Used to read the JSON fixture files this test maps. */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** The mapper under test. */
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

        assertThat(product.getRelatedProducts().getCites()).isNull();
        assertThat(product.getRelatedProducts().getIsPartOf()).hasSize(1);
        ProductsRelatedItem isPartOf = (ProductsRelatedItem) product.getRelatedProducts().getIsPartOf().getFirst();
        assertThat(isPartOf.getIdentifiers().getFirst().getScheme()).isEqualTo("issn");
        assertThat(isPartOf.getIdentifiers().getFirst().getValue()).isEqualTo("2230-9578");
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
        assertThat(product.getRelatedProducts().getCites()).hasSize(expectedCitesCount);
        assertThat(product.getRelatedProducts().getCites())
                .anyMatch(c -> "https://doi.org/10.5281/zenodo.21913675"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
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
        assertThat(isbnCite.getIdentifiers().getFirst().getScheme()).isEqualTo("isbn");
        assertThat(isbnCite.getIdentifiers().getFirst().getValue()).isEqualTo("978-963-281-509-1");
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

        assertThat(related.getIsSupplementedBy()).hasSize(1);
        ProductsRelatedItem isSupplementedBy = (ProductsRelatedItem) related.getIsSupplementedBy().getFirst();
        assertThat(isSupplementedBy.getIdentifiers().getFirst().getScheme()).isEqualTo("url");
        assertThat(isSupplementedBy.getIdentifiers().getFirst().getValue())
                .isEqualTo("https://github.com/vicgos/MICRO");

        assertThat(related.getIsDocumentedBy()).hasSize(1);
        assertThat(((ProductsRelatedItem) related.getIsDocumentedBy().getFirst()).getIdentifiers().getFirst()
                .getScheme()).isEqualTo("handle");

        assertThat(related.getIsNewVersionOf()).hasSize(2);
        assertThat(related.getIsNewVersionOf())
                .anyMatch(r -> "10.18712/NSD-NSD2457-V3"
                        .equals(((ProductsRelatedItem) r).getIdentifiers().getFirst().getValue()));
    }

    @Test
    void mapsIsPartOfWithFullDoiLocalIdentifier() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertThat(related.getIsPartOf()).hasSize(2);
        assertThat(related.getIsPartOf())
                .anyMatch(r -> "https://doi.org/10.5281/zenodo.21827101"
                        .equals(((ProductsRelatedItem) r).getLocalIdentifier()));
    }

    @Test
    void doesNotConfuseIsSupplementToWithIsSupplementedByOrSurfaceHasVersion() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        // "IsSupplementTo" and "IsSupplementedBy" both target the same identifier (10852/56047)
        // in this fixture, so a substring/prefix mixup would silently double it into
        // is_supplemented_by - it must appear there exactly once, from "IsSupplementedBy" only.
        assertThat(related.getIsSupplementedBy()).hasSize(1);
        // Neither "IsSupplementTo" nor "HasVersion" have a related_products field at all -
        // cites/citedBy stay null, not just empty.
        assertThat(related.getCites()).isNull();
    }
}
