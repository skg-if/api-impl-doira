package org.skgif.doi.crossref.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductAllOfIdentifiers;
import org.skgif.doi.mapper.GrantCapableMapper;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Maps a Crossref {@code works} record onto either the SKG-IF {@code Product} entity ({@link
 * #toProduct}) or, for Crossref's {@code type: "grant"} records, the SKG-IF {@code Grant}
 * entity ({@link #toGrant} - see {@link CrossrefTypeMapping#isGrant}). Mirrors {@code
 * DataCiteToSkgIfMapper}'s structure and conventions (otf ids, full-URL local_identifiers for
 * ORCID/ROR/DOI) - see that class's javadoc for the shared rationale. Delegates each mapping
 * concern to a sibling helper class ({@link CrossrefTitleMapper}, {@link
 * CrossrefContributionMapper}, {@link CrossrefManifestationMapper}, {@link CrossrefBiblioMapper},
 * {@link CrossrefFundingMapper}, {@link CrossrefRelatedProductMapper}, {@link
 * CrossrefGrantMapper}, {@link CrossrefGrantContributionMapper}) - this class is just the
 * orchestrator.
 *
 * <p>Known limitations (Crossref has no source for these, left unset rather than guessed at):
 * {@code Product.manifestations[].version} (Crossref doesn't register software-versioning
 * information), {@code Product.relevantOrganisations} (no organisation-level field outside
 * per-contributor affiliations, same gap DataCite has), {@code Grant.acronym} (no equivalent
 * field found in Crossref's grant schema).
 */
@ApplicationScoped
public class CrossrefToSkgIfMapper implements GrantCapableMapper<CrossrefWork> {

    private static final String SCHEME_DOI = "doi";

    private final LocalIdentifiers localIdentifiers;
    private final CrossrefManifestationMapper manifestationMapper;
    private final CrossrefFundingMapper fundingMapper;
    private final CrossrefRelatedProductMapper relatedProductMapper;
    private final CrossrefGrantMapper grantMapper;

    /**
     * @param localIdentifiers builds full/otf local_identifier values for mapped entities
     * @param journalDoiResolver looks up a real journal-level DOI for an article's ISSN(s)
     */
    public CrossrefToSkgIfMapper(LocalIdentifiers localIdentifiers, CrossrefJournalDoiResolver journalDoiResolver) {
        this.localIdentifiers = localIdentifiers;
        this.manifestationMapper =
                new CrossrefManifestationMapper(new CrossrefBiblioMapper(localIdentifiers, journalDoiResolver));
        this.fundingMapper = new CrossrefFundingMapper(localIdentifiers);
        this.relatedProductMapper = new CrossrefRelatedProductMapper(localIdentifiers);
        this.grantMapper = new CrossrefGrantMapper(fundingMapper);
    }

    /**
     * Convenience overload with no XML-parsed venue metadata - equivalent to calling {@link
     * #toProduct(CrossrefWork, CrossrefVenueMetadata)} with a null venueMetadata.
     *
     * @param work the Crossref work record to map
     * @return the mapped Product
     */
    @Override
    public Product toProduct(CrossrefWork work) {
        return toProduct(work, null);
    }

    /**
     * Overload accepting venue metadata parsed from Crossref's XML transform endpoint ({@code
     * application/vnd.crossref.unixsd+xml} - see {@code CrossrefVenueMetadataXmlParser}), fetched
     * only for chapter-in-a-book or paper-in-proceedings records ({@link
     * CrossrefTypeMapping#isXmlVenueEnrichable}). Lets {@link CrossrefBiblioMapper} build an
     * accurate Venue - real container title/DOI/ISBN instead of the ambiguous {@code
     * container-title[0]} - when available; behaves exactly like the single-arg overload when
     * {@code venueMetadata} is {@code null} (e.g. the XML fetch failed, or this isn't an
     * enrichable record).
     *
     * @param work the Crossref work record to map
     * @param venueMetadata venue metadata parsed from Crossref's XML transform endpoint, or null
     * @return the mapped Product
     */
    public Product toProduct(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        Objects.requireNonNull(work.doi(), "Crossref record has no DOI");

        return new Product()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi()))
                .productType(CrossrefTypeMapping.productType(work.type()))
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme(SCHEME_DOI).value(work.doi())))
                .titles(CrossrefTitleMapper.titles(work))
                .abstracts(CrossrefTitleMapper.abstracts(work))
                .topics(CrossrefTitleMapper.topics(work))
                .contributions(CrossrefContributionMapper.contributions(work))
                .manifestations(List.of(manifestationMapper.manifestation(work, venueMetadata)))
                .funding(fundingMapper.funding(work))
                .relatedProducts(relatedProductMapper.relatedProducts(work));
    }

    /**
     * Maps a Crossref {@code type: "grant"} record onto the SKG-IF {@code Grant} entity. Unlike
     * DataCite's Award schema (no dedicated funder/amount/duration fields at all, forcing a
     * "first ROR-bearing creator" heuristic - see {@code DataCiteToSkgIfMapper#toGrant}),
     * Crossref grant records carry these explicitly under {@code project[].funding[]} and
     * {@code project[].award-*}, so no guessing is needed here.
     *
     * <p>A single grant DOI can have multiple {@code project[]} entries (e.g. a joint award); all
     * of them contribute titles/abstracts/contributions/beneficiaries, but the funding
     * amount/currency/duration/scheme are taken from the first project's first funding entry -
     * Crossref gives no generic way to represent "this grant has N different amounts".
     *
     * @param work the Crossref {@code type: "grant"} work record to map
     * @return the mapped Grant
     */
    @Override
    public Grant toGrant(CrossrefWork work) {
        Objects.requireNonNull(work.doi(), "Crossref record has no DOI");

        List<CrossrefProject> projects = work.project() != null ? work.project() : List.of();
        CrossrefProject primaryProject = projects.isEmpty() ? null : projects.get(0);
        CrossrefFunding primaryFunding = primaryProject != null && primaryProject.funding() != null
                && !primaryProject.funding().isEmpty() ? primaryProject.funding().get(0) : null;

        return new Grant()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi()))
                .entityType(Grant.EntityTypeEnum.GRANT)
                .identifiers(List.of(new GrantLiteAllOfIdentifiers().scheme(SCHEME_DOI).value(work.doi())))
                .titles(grantMapper.grantTitles(projects))
                .abstracts(grantMapper.grantAbstracts(projects))
                .grantNumber(work.award())
                .fundingAgency(grantMapper.grantFundingAgency(work.doi(), primaryFunding, work.funder()))
                .fundingStream(primaryFunding != null ? primaryFunding.scheme() : null)
                .fundedAmount(grantMapper.fundedAmount(primaryProject, primaryFunding))
                .currency(grantMapper.currency(primaryProject, primaryFunding))
                .duration(grantMapper.duration(primaryProject))
                .website(grantMapper.website(work))
                .contributions(CrossrefGrantContributionMapper.grantContributions(work.doi(), projects))
                .beneficiaries(CrossrefGrantContributionMapper.grantBeneficiaries(work.doi(), projects));
    }
}
