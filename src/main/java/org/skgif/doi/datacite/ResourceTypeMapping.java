package org.skgif.doi.datacite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.generated.model.Product;

/**
 * Single source of truth for how DataCite's {@code resourceTypeGeneral} controlled vocabulary
 * maps onto SKG-IF's 4-value {@code product_type} enum, and for recognizing DataCite's
 * {@code "Award"} type - which isn't a product at all, it's routed to the Grants endpoint
 * instead (see {@code GrantsResource}/{@code ProductsResource}).
 *
 * <p>Used both by {@code DataCiteToSkgIfMapper} (forward: resourceTypeGeneral -> product_type)
 * and {@code ProductFilters} (reverse: product_type -> the DataCite values that produce it),
 * so the two never drift apart.
 */
public final class ResourceTypeMapping {

    public static final String AWARD = "Award";

    private static final Map<String, Product.ProductTypeEnum> TO_PRODUCT_TYPE = buildMap();

    private ResourceTypeMapping() {
    }

    private static Map<String, Product.ProductTypeEnum> buildMap() {
        Map<String, Product.ProductTypeEnum> map = new HashMap<>();
        putAll(map, Product.ProductTypeEnum.RESEARCH_SOFTWARE, "Software", "ComputationalNotebook", "Workflow");
        putAll(map, Product.ProductTypeEnum.LITERATURE, "Book", "BookChapter", "ConferencePaper",
                "ConferenceProceeding", "DataPaper", "Dissertation", "JournalArticle", "Journal", "Preprint",
                "Report", "Text", "PeerReview", "StudyRegistration", "OutputManagementPlan");
        putAll(map, Product.ProductTypeEnum.RESEARCH_DATA, "Dataset", "Collection", "Image");
        putAll(map, Product.ProductTypeEnum.OTHER, "Event", "Service", "Project", "Other", "Sound",
                "PhysicalObject", "Model", "Audiovisual", "InteractiveResource", "Standard");
        return Map.copyOf(map);
    }

    private static void putAll(Map<String, Product.ProductTypeEnum> map, Product.ProductTypeEnum value,
            String... resourceTypes) {
        for (String resourceType : resourceTypes) {
            map.put(resourceType, value);
        }
    }

    /** {@code Award} is deliberately absent from the map above - never reaches this method. */
    public static Product.ProductTypeEnum productType(String resourceTypeGeneral) {
        return TO_PRODUCT_TYPE.getOrDefault(resourceTypeGeneral, Product.ProductTypeEnum.OTHER);
    }

    public static boolean isAward(DataCiteAttributes attributes) {
        return attributes != null && attributes.types != null && AWARD.equalsIgnoreCase(attributes.types.resourceTypeGeneral);
    }

    /**
     * The DataCite {@code resourceTypeGeneral} values that map to the given SKG-IF
     * product_type, alphabetically sorted so callers building a query clause from this (see
     * {@code ProductFilters}) get deterministic output regardless of the backing map's
     * iteration order.
     */
    public static List<String> resourceTypesFor(Product.ProductTypeEnum productType) {
        return TO_PRODUCT_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue() == productType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
