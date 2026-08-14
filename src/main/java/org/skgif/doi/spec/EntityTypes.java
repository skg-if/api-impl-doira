package org.skgif.doi.spec;

/**
 * The SKG-IF {@code entity_type} discriminator values used across every provider's mapper - single
 * source of truth so the Crossref and DataCite mappers never drift apart on these strings.
 */
public final class EntityTypes {

    public static final String ORGANISATION = "organisation";
    public static final String PRODUCT = "product";
    public static final String PERSON = "person";
    public static final String VENUE = "venue";

    private EntityTypes() {
    }
}
