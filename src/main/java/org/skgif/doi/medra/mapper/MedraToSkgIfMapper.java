package org.skgif.doi.medra.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductAllOfIdentifiers;
import org.skgif.doi.mapper.RegistrationAgencyMapper;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Maps a mEDRA ONIX-for-DOI record ({@link MedraWork}, built by {@code MedraOnixXmlParser}) onto
 * the SKG-IF {@code Product} entity. Mirrors {@code CrossrefToSkgIfMapper}/{@code
 * DataCiteToSkgIfMapper}'s conventions (otf ids, full-URL local_identifiers for DOIs) where the
 * source format supports them. Delegates each mapping concern to a sibling helper class ({@link
 * MedraTitleMapper}, {@link MedraContributionMapper}, {@link MedraManifestationMapper}, {@link
 * MedraBiblioMapper}) - this class is just the orchestrator.
 *
 * <p>No {@code toGrant} - ONIX-for-DOI has no funding/grant/project element of any kind (verified
 * against 7 live examples), and {@code api.medra.org/metadata/{doi}} has no query mechanism to
 * discover grant-type records even hypothetically. See SKG_IF_DOI_MAPPING_LIMITATIONS.md.
 *
 * <p>Known limitations (mEDRA has no source for these, left unset rather than guessed at): {@code
 * topics[].term}, {@code manifestations[].biblio.issue/volume/pages}, {@code
 * manifestations[].version}, {@code funding[]}, {@code relatedProducts}, {@code
 * relevantOrganisations} - none observed in the fixtures examined. Only the ONIX-for-DOI "Serial
 * Article" schema is handled (the only variant seen live) - other schema families (Book,
 * ConferenceProceeding, Dissertation) aren't parsed and degrade to not-found upstream.
 */
@ApplicationScoped
public class MedraToSkgIfMapper implements RegistrationAgencyMapper<MedraWork> {

    /** Builds full/otf local_identifier values for mapped entities. */
    private final LocalIdentifiers localIdentifiers;

    /**
     * @param localIdentifiers builds full/otf local_identifier values for mapped entities
     */
    public MedraToSkgIfMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    /**
     * @param work the mEDRA record to map
     * @return the mapped Product
     */
    @Override
    public Product toProduct(MedraWork work) {
        Objects.requireNonNull(work.doi(), "mEDRA record has no DOI");

        return new Product()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi()))
                .productType(Product.ProductTypeEnum.LITERATURE)
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme(IdentifierScheme.DOI.value()).value(work.doi())))
                .titles(MedraTitleMapper.titles(work))
                .abstracts(MedraTitleMapper.abstracts(work))
                .contributions(MedraContributionMapper.contributions(work))
                .manifestations(List.of(MedraManifestationMapper.manifestation(work)));
    }
}
