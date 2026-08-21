package org.skgif.doi.datacite;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.generated.model.Product;

/**
 * Single source of truth for how DataCite's {@code resourceTypeGeneral} controlled vocabulary
 * maps onto SKG-IF's 4-value {@code product_type} enum, and for recognizing DataCite's
 * {@code "Award"} type - which isn't a product at all, it's routed to the Grants endpoint
 * instead (see {@code DataCiteGrantsResource}/{@code DataCiteProductsResource}).
 *
 * <p>Used both by {@code DataCiteToSkgIfMapper} (forward: resourceTypeGeneral -> product_type)
 * and {@code DataCiteProductFilters} (reverse: product_type -> the DataCite values that produce it),
 * so the two never drift apart.
 */
public final class ResourceTypeMapping {

    /** DataCite's {@code resourceTypeGeneral} value routed to the Grants endpoint, not products. */
    public static final String AWARD = DataCiteResourceType.AWARD.value();

    /** Reverse lookup from a {@link DataCiteResourceType} to its SKG-IF {@code product_type}. */
    private static final Map<DataCiteResourceType, Product.ProductTypeEnum> TO_PRODUCT_TYPE = buildMap();

    private ResourceTypeMapping() {
    }

    /**
     * The {@code resourceTypeGeneral} values documented in the DataCite Metadata Schema
     * (https://datacite-metadata-schema.readthedocs.io/en/4.7/properties/resourcetype/#a-resourcetypegeneral).
     * DataCite has added values to this list before and may do so again - {@link
     * #fromValue(String)} returns {@link Optional#empty()} rather than throwing for a value not
     * yet in this enum, so a DataCite record using a newer value falls back to {@code OTHER}
     * (see {@link #productType(String)}) instead of failing the mapping.
     */
    private enum DataCiteResourceType {

        AUDIOVISUAL("Audiovisual"),
        AWARD("Award"),
        BOOK("Book"),
        BOOK_CHAPTER("BookChapter"),
        COLLECTION("Collection"),
        COMPUTATIONAL_NOTEBOOK("ComputationalNotebook"),
        CONFERENCE_PAPER("ConferencePaper"),
        CONFERENCE_PROCEEDING("ConferenceProceeding"),
        DATA_PAPER("DataPaper"),
        DATASET("Dataset"),
        DISSERTATION("Dissertation"),
        EVENT("Event"),
        IMAGE("Image"),
        INTERACTIVE_RESOURCE("InteractiveResource"),
        INSTRUMENT("Instrument"),
        JOURNAL("Journal"),
        JOURNAL_ARTICLE("JournalArticle"),
        MODEL("Model"),
        OUTPUT_MANAGEMENT_PLAN("OutputManagementPlan"),
        PEER_REVIEW("PeerReview"),
        PHYSICAL_OBJECT("PhysicalObject"),
        POSTER("Poster"),
        PREPRINT("Preprint"),
        PRESENTATION("Presentation"),
        PROJECT("Project"),
        REPORT("Report"),
        SERVICE("Service"),
        SOFTWARE("Software"),
        SOUND("Sound"),
        STANDARD("Standard"),
        STUDY_REGISTRATION("StudyRegistration"),
        TEXT("Text"),
        WORKFLOW("Workflow"),
        OTHER("Other");

        /** Reverse lookup from {@link #value()} back to the enum constant. */
        private static final Map<String, DataCiteResourceType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(DataCiteResourceType::value, Function.identity()));

        /** The constant's underlying DataCite {@code resourceTypeGeneral} value. */
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private final String value;

        DataCiteResourceType(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        static Optional<DataCiteResourceType> fromValue(String value) {
            return Optional.ofNullable(BY_VALUE.get(value));
        }
    }

    private static Map<DataCiteResourceType, Product.ProductTypeEnum> buildMap() {
        Map<DataCiteResourceType, Product.ProductTypeEnum> map = new HashMap<>();
        putAll(map, Product.ProductTypeEnum.RESEARCH_SOFTWARE, DataCiteResourceType.SOFTWARE,
                DataCiteResourceType.COMPUTATIONAL_NOTEBOOK, DataCiteResourceType.WORKFLOW);
        putAll(map, Product.ProductTypeEnum.LITERATURE, DataCiteResourceType.BOOK, DataCiteResourceType.BOOK_CHAPTER,
                DataCiteResourceType.CONFERENCE_PAPER, DataCiteResourceType.CONFERENCE_PROCEEDING,
                DataCiteResourceType.DATA_PAPER, DataCiteResourceType.DISSERTATION,
                DataCiteResourceType.JOURNAL_ARTICLE, DataCiteResourceType.JOURNAL, DataCiteResourceType.PREPRINT,
                DataCiteResourceType.REPORT, DataCiteResourceType.TEXT, DataCiteResourceType.PEER_REVIEW,
                DataCiteResourceType.STUDY_REGISTRATION, DataCiteResourceType.OUTPUT_MANAGEMENT_PLAN,
                DataCiteResourceType.POSTER, DataCiteResourceType.PRESENTATION);
        putAll(map, Product.ProductTypeEnum.RESEARCH_DATA, DataCiteResourceType.DATASET,
                DataCiteResourceType.COLLECTION, DataCiteResourceType.IMAGE);
        putAll(map, Product.ProductTypeEnum.OTHER, DataCiteResourceType.EVENT, DataCiteResourceType.SERVICE,
                DataCiteResourceType.PROJECT, DataCiteResourceType.OTHER, DataCiteResourceType.SOUND,
                DataCiteResourceType.PHYSICAL_OBJECT, DataCiteResourceType.MODEL, DataCiteResourceType.AUDIOVISUAL,
                DataCiteResourceType.INTERACTIVE_RESOURCE, DataCiteResourceType.STANDARD,
                DataCiteResourceType.INSTRUMENT);
        return Map.copyOf(map);
    }

    private static void putAll(Map<DataCiteResourceType, Product.ProductTypeEnum> map, Product.ProductTypeEnum value,
            DataCiteResourceType... resourceTypes) {
        for (DataCiteResourceType resourceType : resourceTypes) {
            map.put(resourceType, value);
        }
    }

    /**
     * A {@code resourceTypeGeneral} value DataCite has added since this enum was last updated
     * (or any other unrecognized value) resolves to {@code OTHER} rather than throwing.
     *
     * @param resourceTypeGeneral the DataCite {@code resourceTypeGeneral} value
     * @return the corresponding SKG-IF product_type, or OTHER if unrecognized
     */
    public static Product.ProductTypeEnum productType(String resourceTypeGeneral) {
        return DataCiteResourceType.fromValue(resourceTypeGeneral)
                .map(TO_PRODUCT_TYPE::get)
                .orElse(Product.ProductTypeEnum.OTHER);
    }

    /**
     * @param attributes the DataCite record's attributes to check
     * @return whether attributes' {@code resourceTypeGeneral} is {@code Award}
     */
    public static boolean isAward(DataCiteAttributes attributes) {
        if (attributes == null || attributes.types() == null) {
            return false;
        }
        return DataCiteResourceType.fromValue(attributes.types().resourceTypeGeneral())
                .filter(type -> type == DataCiteResourceType.AWARD)
                .isPresent();
    }

    /**
     * The DataCite {@code resourceTypeGeneral} values that map to the given SKG-IF
     * product_type, alphabetically sorted so callers building a query clause from this (see
     * {@code DataCiteProductFilters}) get deterministic output regardless of the backing map's
     * iteration order.
     *
     * @param productType the SKG-IF product_type to find matching DataCite values for
     * @return the matching resourceTypeGeneral values, alphabetically sorted
     */
    public static List<String> resourceTypesFor(Product.ProductTypeEnum productType) {
        return TO_PRODUCT_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue() == productType)
                .map(entry -> entry.getKey().value())
                .sorted()
                .toList();
    }
}
