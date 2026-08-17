package org.skgif.doi.datacite.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteContributor;
import org.skgif.doi.datacite.dto.DataCiteCreator;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductAllOfIdentifiers;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Maps a DataCite DOI record ({@code attributes}) onto either the SKG-IF {@code Product}
 * entity ({@link #toProduct}) or, for DataCite's {@code resourceTypeGeneral: "Award"} records,
 * the SKG-IF {@code Grant} entity ({@link #toGrant} - see {@link ResourceTypeMapping#isAward}).
 * Delegates each mapping concern to a sibling helper class ({@link DataCiteTitleMapper}, {@link
 * DataCiteContributionMapper}, {@link DataCiteManifestationMapper}, {@link DataCiteBiblioMapper},
 * {@link DataCiteFundingMapper}, {@link DataCiteRelatedProductMapper}, {@link
 * DataCiteGrantMapper}) - this class is just the orchestrator.
 *
 * <p>Every nested entity (person, organisation, grant, data source, topic, related product)
 * requires {@code local_identifier} + {@code entity_type} per the spec's own schema, even when
 * embedded inline. Where we have a genuine external identifier (ORCID, ROR, DOI) it's used
 * directly as a dereferenceable URL, matching how the top-level Product itself uses its DOI.
 * Where we don't (e.g. a free-text DataCite subject has no real identifier system behind it),
 * an "on-the-fly" ({@code otf___<doi>___<slug>}) identifier is generated per the spec's own
 * convention for entities lacking a stable identifier - deterministic per product rather than
 * random, so repeated calls for the same DOI produce byte-identical output.
 *
 * <p>Known limitation on {@link #toGrant}: DataCite's Award schema has no generic source for
 * {@code grant_number}, {@code currency}, {@code funded_amount}, {@code duration}, {@code
 * website}, {@code funding_stream} or {@code acronym} - these are left unset rather than
 * guessed at.
 */
@ApplicationScoped
public class DataCiteToSkgIfMapper {

    private static final String SCHEME_DOI = "doi";

    private final LocalIdentifiers localIdentifiers;
    private final DataCiteFundingMapper fundingMapper;
    private final DataCiteRelatedProductMapper relatedProductMapper;

    /**
     * @param localIdentifiers builds full/otf local_identifier values for mapped entities
     */
    public DataCiteToSkgIfMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
        this.fundingMapper = new DataCiteFundingMapper(localIdentifiers);
        this.relatedProductMapper = new DataCiteRelatedProductMapper(localIdentifiers);
    }

    /**
     * @param attributes the DataCite record's attributes to map
     * @return the mapped Product
     */
    public Product toProduct(DataCiteAttributes attributes) {
        Objects.requireNonNull(attributes.doi(), "DataCite record has no DOI");

        return new Product()
                // Full https://doi.org/... form, consistent with every other entity in this
                // output (Person -> ORCID URL, Organisation -> ROR URL): use the full external
                // identifier URL as local_identifier whenever we have a real one.
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(attributes.doi()))
                .productType(ResourceTypeMapping.productType(
                        DataCiteManifestationMapper.resourceTypeGeneral(attributes)))
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme(SCHEME_DOI).value(attributes.doi())))
                .titles(DataCiteTitleMapper.titles(attributes))
                .abstracts(DataCiteTitleMapper.abstracts(attributes))
                .topics(DataCiteTitleMapper.topics(attributes))
                .contributions(DataCiteContributionMapper.contributions(attributes))
                .manifestations(List.of(DataCiteManifestationMapper.manifestation(attributes)))
                .funding(fundingMapper.funding(attributes))
                .relatedProducts(relatedProductMapper.relatedProducts(attributes));
    }

    /**
     * Maps a DataCite Award record ({@code resourceTypeGeneral: "Award"}) onto the SKG-IF
     * {@code Grant} entity. The DataCite Award schema has no dedicated "who funds this" field,
     * so the real-world convention this follows (confirmed against live Award DOIs from
     * multiple DataCite members) is: the first creator carrying a ROR identifier is the
     * funding body itself; every other creator plus all contributors are the grant's
     * contributions; organisational contributors are also listed as beneficiaries.
     *
     * @param attributes the DataCite Award record to map
     * @return the mapped Grant
     */
    public Grant toGrant(DataCiteAttributes attributes) {
        Objects.requireNonNull(attributes.doi(), "DataCite record has no DOI");

        List<DataCiteCreator> creators = attributes.creators() != null ? attributes.creators() : List.of();
        List<DataCiteContributor> contributors = attributes.contributors() != null
                ? attributes.contributors()
                : List.of();
        Optional<DataCiteCreator> fundingAgencyCreator = creators.stream()
                .filter(c -> DataCiteContributionMapper.firstRor(c.nameIdentifiers()) != null)
                .findFirst();

        return new Grant()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(attributes.doi()))
                .entityType(Grant.EntityTypeEnum.GRANT)
                .identifiers(List.of(new GrantLiteAllOfIdentifiers().scheme(SCHEME_DOI).value(attributes.doi())))
                .titles(DataCiteTitleMapper.grantTitles(attributes))
                .abstracts(DataCiteTitleMapper.grantAbstracts(attributes))
                .fundingAgency(DataCiteGrantMapper.grantFundingAgency(
                        attributes.doi(), fundingAgencyCreator, attributes.publisher()))
                .contributions(DataCiteGrantMapper.grantContributions(
                        attributes.doi(), creators, contributors, fundingAgencyCreator))
                .beneficiaries(DataCiteGrantMapper.grantBeneficiaries(attributes.doi(), contributors));
    }
}
