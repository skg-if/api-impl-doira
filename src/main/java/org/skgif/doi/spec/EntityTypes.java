package org.skgif.doi.spec;

/**
 * The SKG-IF {@code entity_type} discriminator values used across every provider's mapper - single
 * source of truth so the Crossref and DataCite mappers never drift apart on these strings.
 */
public enum EntityTypes {

    ORGANISATION("organisation"),
    PRODUCT("product"),
    PERSON("person"),
    VENUE("venue");

    // Field intentionally shares its name with its accessor below, same idiom
    // CrossrefFilters.ParsedFilter.Builder's fields already do this for.
    /** The constant's underlying SKG-IF {@code entity_type} string. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String value;

    EntityTypes(String value) {
        this.value = value;
    }

    /**
     * Unwraps the constant to the raw string the SKG-IF spec expects.
     *
     * @return the SKG-IF {@code entity_type} string this constant represents
     */
    public String value() {
        return value;
    }
}
