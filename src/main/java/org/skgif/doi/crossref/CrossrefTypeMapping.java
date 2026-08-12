package org.skgif.doi.crossref;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.Product;

/**
 * Single source of truth for how Crossref's {@code type} controlled vocabulary (fetched live
 * from {@code api.crossref.org/types} while planning this) maps onto SKG-IF's 4-value {@code
 * product_type} enum, and for recognizing Crossref's {@code "grant"} type - which isn't a
 * product at all, it's routed to the Grants endpoint instead (see {@code
 * CrossrefGrantsResource}/{@code CrossrefProductsResource}), mirroring {@code
 * ResourceTypeMapping} on the DataCite side.
 *
 * <p>Unlike DataCite, Crossref has no {@code software} type in its vocabulary at all - Crossref
 * does not register software-specific DOIs the way DataCite does, so {@code research software}
 * is effectively unreachable via this provider alone.
 */
public final class CrossrefTypeMapping {

    public static final String GRANT = "grant";

    private static final Map<String, Product.ProductTypeEnum> TO_PRODUCT_TYPE = buildMap();

    private CrossrefTypeMapping() {
    }

    private static Map<String, Product.ProductTypeEnum> buildMap() {
        Map<String, Product.ProductTypeEnum> map = new HashMap<>();
        putAll(map, Product.ProductTypeEnum.RESEARCH_DATA, "dataset", "database", "standard");
        putAll(map, Product.ProductTypeEnum.LITERATURE, "journal-article", "book", "book-chapter",
                "book-section", "book-part", "book-series", "book-set", "book-track", "monograph",
                "edited-book", "reference-book", "reference-entry", "proceedings", "proceedings-article",
                "proceedings-series", "report", "report-series", "report-component", "dissertation",
                "peer-review", "posted-content", "journal", "journal-issue", "journal-volume", "component");
        putAll(map, Product.ProductTypeEnum.OTHER, "other");
        return Map.copyOf(map);
    }

    private static void putAll(Map<String, Product.ProductTypeEnum> map, Product.ProductTypeEnum value,
            String... types) {
        for (String type : types) {
            map.put(type, value);
        }
    }

    /** {@code grant} is deliberately absent from the map above - never reaches this method. */
    public static Product.ProductTypeEnum productType(String type) {
        return TO_PRODUCT_TYPE.getOrDefault(type, Product.ProductTypeEnum.OTHER);
    }

    public static boolean isGrant(CrossrefWork work) {
        return work != null && GRANT.equalsIgnoreCase(work.type);
    }

    /**
     * The Crossref {@code type} values that map to the given SKG-IF product_type, alphabetically
     * sorted so callers building a query clause from this (see {@code CrossrefFilters}) get
     * deterministic output regardless of the backing map's iteration order.
     */
    public static List<String> typesFor(Product.ProductTypeEnum productType) {
        return TO_PRODUCT_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue() == productType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
