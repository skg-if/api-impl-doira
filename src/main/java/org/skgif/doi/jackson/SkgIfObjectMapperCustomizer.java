package org.skgif.doi.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.generated.model.GrantContributionBy;
import org.skgif.doi.generated.model.ProductContributionBy;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * {@code ProductContributionBy} and {@code GrantContributionBy} are openapi-generator's merge
 * of their respective spec's {@code by} oneOf (PersonLite|Organisation|Agent|LocalIdentifierRef)
 * - the only two {@code by}-shaped properties in the spec whose schema declares an explicit
 * {@code discriminator}, which is why only these two (not e.g. {@code
 * ProductAllOfRelevantOrganisations}/{@code GrantAllOfBeneficiaries}, whose {@code oneOf} has no
 * discriminator) get a generated Jackson {@code @JsonTypeInfo(property = "entity_type")}
 * polymorphic discriminator, now on the interface itself (since {@code useOneOfInterfaces}).
 *
 * <p>Two problems, both worked around here:
 *
 * <p>1) Serializing a value through one of these interfaces uses Jackson's own polymorphic type
 * resolution, which - for any runtime type not perfectly matching the {@code @JsonSubTypes}
 * mapping - falls back to writing the class's {@code @JsonTypeName} (e.g. "ProductContribution_by")
 * into {@code entity_type} instead of a real value. Forcing {@code Id.NONE} disables that,
 * falling back to plain bean-property serialization of {@code entityType} instead - i.e. exactly
 * whatever the mapper explicitly set.
 *
 * <p>2) The interfaces are ALSO annotated {@code @JsonIgnoreProperties(value = "entity_type",
 * allowSetters = true)}, generated on the assumption that Jackson's polymorphic handling (problem
 * 1) is what's responsible for writing {@code entity_type}. Because {@code PersonLite}/{@code
 * Organisation}/{@code Agent} implement these interfaces, Jackson inherits that ignore rule for
 * {@code entity_type} on those classes GLOBALLY - not just when serialized through {@code by}, but
 * everywhere else they appear as a plain property (funding_agency, beneficiaries,
 * declared_affiliations, relevant_organisations) - silently dropping {@code entity_type} there
 * too. Re-declaring {@code @JsonIgnoreProperties({})} (empty) on the same mixin cancels that
 * inherited rule, restoring normal serialization of the mapper-set value everywhere.
 */
@Singleton
public class SkgIfObjectMapperCustomizer implements ObjectMapperCustomizer {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonIgnoreProperties({})
    private interface NoPolymorphicTypeInfo {
    }

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.addMixIn(ProductContributionBy.class, NoPolymorphicTypeInfo.class);
        objectMapper.addMixIn(GrantContributionBy.class, NoPolymorphicTypeInfo.class);
    }
}
