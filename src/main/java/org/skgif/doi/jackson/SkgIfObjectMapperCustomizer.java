package org.skgif.doi.jackson;

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
 * polymorphic discriminator. Serializing a plain instance of either (its own runtime class
 * matches none of the registered subtypes) makes Jackson fall back to writing the class's
 * {@code @JsonTypeName} (e.g. "ProductContribution_by") into entity_type - not a real value we
 * ever want to emit. This mixin disables that polymorphic handling on both so entity_type
 * serializes as the plain bean property instead (unset for persons - see
 * {@code DataCiteToSkgIfMapper}'s javadoc - and correctly "organisation" for organisations).
 */
@Singleton
public class SkgIfObjectMapperCustomizer implements ObjectMapperCustomizer {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private interface NoPolymorphicTypeInfo {
    }

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.addMixIn(ProductContributionBy.class, NoPolymorphicTypeInfo.class);
        objectMapper.addMixIn(GrantContributionBy.class, NoPolymorphicTypeInfo.class);
    }
}
