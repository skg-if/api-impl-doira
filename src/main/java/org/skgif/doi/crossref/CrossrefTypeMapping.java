package org.skgif.doi.crossref;

import static org.skgif.doi.util.SpotBugsSuppressions.IMPROPER_UNICODE;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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

    /** Crossref's {@code type} value routed to the Grants endpoint, not products. */
    public static final String GRANT = "grant";

    /**
     * Crossref types whose XML representation nests the actual record inside a container element
     * - a chapter-like {@code content_item} inside a {@code <book>} (with its own {@code
     * book_series_metadata}/{@code book_metadata} sibling), or a {@code conference_paper} inside
     * a {@code <conference>} (with its own {@code proceedings_series_metadata}/{@code
     * proceedings_metadata} sibling) - carrying the container's title/DOI/ISBN far less
     * ambiguously than the REST JSON's {@code container-title[]} array does. See {@code
     * CrossrefVenueMetadataXmlParser}. {@code book}/{@code proceedings}/{@code proceedings-series}
     * are deliberately excluded: their own venue is themselves, not a container they sit inside.
     */
    private static final Set<String> XML_VENUE_ENRICHABLE_TYPES =
            Set.of("book-chapter", "book-section", "book-part", "reference-entry", "proceedings-article");

    /** Reverse lookup from a Crossref {@code type} value to its SKG-IF {@code product_type}. */
    private static final Map<String, Product.ProductTypeEnum> TO_PRODUCT_TYPE = buildMap();

    private CrossrefTypeMapping() {
    }

    private static Map<String, Product.ProductTypeEnum> buildMap() {
        Map<String, Product.ProductTypeEnum> map = new HashMap<>();
        putAll(map, Product.ProductTypeEnum.RESEARCH_DATA, "dataset");
        putAll(map, Product.ProductTypeEnum.LITERATURE, "journal-article", "book", "book-chapter",
                "book-section", "book-part", "book-series", "book-set", "book-track", "monograph",
                "edited-book", "reference-book", "reference-entry", "proceedings", "proceedings-article",
                "proceedings-series", "report", "report-series", "report-component", "dissertation",
                "peer-review", "posted-content", "journal", "journal-issue", "journal-volume", "component");
        putAll(map, Product.ProductTypeEnum.OTHER, "other", "database", "standard");
        return Map.copyOf(map);
    }

    private static void putAll(Map<String, Product.ProductTypeEnum> map, Product.ProductTypeEnum value,
            String... types) {
        for (String type : types) {
            map.put(type, value);
        }
    }

    /**
     * {@code grant} is deliberately absent from the map above - never reaches this method.
     *
     * @param type the Crossref work's raw {@code type} value
     * @return the corresponding SKG-IF product_type, or OTHER if unrecognized
     */
    public static Product.ProductTypeEnum productType(@Nullable String type) {
        return TO_PRODUCT_TYPE.getOrDefault(type, Product.ProductTypeEnum.OTHER);
    }

    /**
     * Recognizes the Crossref type that routes a record to the Grants endpoint rather than Products.
     *
     * @param work the Crossref work record to check
     * @return whether work's raw {@code type} is {@code grant}
     */
    @SuppressFBWarnings(value = IMPROPER_UNICODE, justification = "equalsIgnoreCase against a fixed ASCII " +
            "vocabulary constant (\"grant\") - none of the Turkish-i/German-ss-style case-folding ambiguity this " +
            "detector exists to catch applies here; unconditionally flagged by design - " + SPOTBUGS_REGISTER)
    public static boolean isGrant(CrossrefWork work) {
        return work != null && GRANT.equalsIgnoreCase(work.type());
    }

    /**
     * Whether this record is a chapter-in-a-book or paper-in-proceedings item, for which an
     * accurate Venue requires fetching Crossref's XML transform (see {@code
     * CrossrefXmlTransformClient}) rather than relying on the ambiguous {@code container-title[]}
     * REST JSON array alone.
     *
     * @param work the Crossref work record to check
     * @return true if work is a chapter-in-a-book or paper-in-proceedings type
     */
    public static boolean isXmlVenueEnrichable(CrossrefWork work) {
        return work != null && XML_VENUE_ENRICHABLE_TYPES.contains(work.type());
    }

    /**
     * The Crossref {@code type} values that map to the given SKG-IF product_type, alphabetically
     * sorted so callers building a query clause from this (see {@code CrossrefFilters}) get
     * deterministic output regardless of the backing map's iteration order.
     *
     * @param productType the SKG-IF product_type to find matching Crossref types for
     * @return the matching Crossref type values, alphabetically sorted
     */
    public static List<String> typesFor(Product.ProductTypeEnum productType) {
        return TO_PRODUCT_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue() == productType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
