package org.skgif.doi.mapper;

import org.skgif.doi.generated.model.Product;

/**
 * Common contract implemented by every registration-agency mapper ({@code
 * DataCiteToSkgIfMapper}, {@code CrossrefToSkgIfMapper}, {@code MedraToSkgIfMapper}) - each maps
 * its provider's DOI record ({@code T}) onto the shared SKG-IF {@code Product} entity.
 * Providers that also support grants additionally implement {@link GrantCapableMapper}.
 *
 * @param <T> the provider-specific DOI record type this mapper accepts
 */
public interface RegistrationAgencyMapper<T> {

    /**
     * @param input the provider's DOI record to map
     * @return the mapped Product
     */
    Product toProduct(T input);
}
