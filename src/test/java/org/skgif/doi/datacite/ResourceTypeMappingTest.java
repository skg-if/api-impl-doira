package org.skgif.doi.datacite;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.generated.model.Product;

final class ResourceTypeMappingTest {

    @CsvSource({"Dataset, RESEARCH_DATA", "Instrument, OTHER", "JournalArticle, LITERATURE", "Other, OTHER",
            "Poster, LITERATURE", "Presentation, LITERATURE", "Software, RESEARCH_SOFTWARE",
            "SomeFutureDataCiteType, OTHER"})
    @ParameterizedTest
    void mapsResourceTypeGeneralToProductType(String resourceTypeGeneral, Product.ProductTypeEnum expected) {
        assertThat(ResourceTypeMapping.productType(resourceTypeGeneral)).isEqualTo(expected);
    }

    @Test
    void recognizesAward() {
        assertThat(ResourceTypeMapping.isAward(attributesWithResourceTypeGeneral("Award"))).isTrue();
    }

    @Test
    void doesNotRecognizeNonAwardAsAward() {
        assertThat(ResourceTypeMapping.isAward(attributesWithResourceTypeGeneral("Dataset"))).isFalse();
    }

    @Test
    void doesNotRecognizeMissingTypesAsAward() {
        assertThat(ResourceTypeMapping.isAward(null)).isFalse();
        assertThat(ResourceTypeMapping.isAward(attributesWithResourceTypeGeneral(null))).isFalse();
    }

    @Test
    void resourceTypesForIncludesReclassifiedPresentationTypes() {
        assertThat(ResourceTypeMapping.resourceTypesFor(Product.ProductTypeEnum.LITERATURE))
                .contains("Poster", "Presentation")
                .isSorted();
    }

    private static DataCiteAttributes attributesWithResourceTypeGeneral(@Nullable String resourceTypeGeneral) {
        return new DataCiteAttributes(null, null, null, null, null, null, null, null, null, null, null, null, null,
                new DataCiteAttributes.Types(resourceTypeGeneral, null), null, null, null, null, null, null);
    }
}
