package org.skgif.doi.mapper;

import org.skgif.doi.generated.model.Grant;

/**
 * Extends {@link RegistrationAgencyMapper} for registration agencies whose schema has a grant
 * concept ({@code DataCiteToSkgIfMapper}, {@code CrossrefToSkgIfMapper}). A provider without one
 * (e.g. {@code MedraToSkgIfMapper} - ONIX-for-DOI has no funding/grant/project element) simply
 * doesn't implement this interface, making the omission a compile-time fact rather than a
 * javadoc note.
 *
 * @param <T> the provider-specific DOI record type this mapper accepts
 */
public interface GrantCapableMapper<T> extends RegistrationAgencyMapper<T> {

    /**
     * @param input the provider's DOI record to map
     * @return the mapped Grant
     */
    Grant toGrant(T input);
}
