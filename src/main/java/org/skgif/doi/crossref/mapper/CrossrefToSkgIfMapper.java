package org.skgif.doi.crossref.mapper;

import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.dto.CrossrefAffiliation;
import org.skgif.doi.crossref.dto.CrossrefAmount;
import org.skgif.doi.crossref.dto.CrossrefContributor;
import org.skgif.doi.crossref.dto.CrossrefDate;
import org.skgif.doi.crossref.dto.CrossrefFunder;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefInvestigator;
import org.skgif.doi.crossref.dto.CrossrefLicense;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.crossref.dto.CrossrefReference;
import org.skgif.doi.crossref.dto.CrossrefUpdateTo;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.EntityIdentifiersInner;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantAllOfBeneficiaries;
import org.skgif.doi.generated.model.GrantAllOfContributions;
import org.skgif.doi.generated.model.GrantAllOfDuration;
import org.skgif.doi.generated.model.GrantContributionBy;
import org.skgif.doi.generated.model.GrantLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductAllOfFunding;
import org.skgif.doi.generated.model.ProductAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductAllOfRelevantOrganisations;
import org.skgif.doi.generated.model.ProductAllOfTerm;
import org.skgif.doi.generated.model.ProductAllOfTopics;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.generated.model.ProductManifestationBiblioIn;
import org.skgif.doi.generated.model.ProductManifestationBiblioPages;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.generated.model.ProductsRelated;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.util.LocalIdentifiers;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps a Crossref {@code works} record onto either the SKG-IF {@code Product} entity ({@link
 * #toProduct}) or, for Crossref's {@code type: "grant"} records, the SKG-IF {@code Grant}
 * entity ({@link #toGrant} - see {@link CrossrefTypeMapping#isGrant}). Mirrors {@code
 * DataCiteToSkgIfMapper}'s structure and conventions (otf ids, full-URL local_identifiers for
 * ORCID/ROR/DOI) - see that class's javadoc for the shared rationale.
 *
 * <p>Known limitations (Crossref has no source for these, left unset rather than guessed at):
 * {@code Product.manifestations[].version} (Crossref doesn't register software-versioning
 * information), {@code Product.relevantOrganisations} (no organisation-level field outside
 * per-contributor affiliations, same gap DataCite has), {@code Grant.acronym} (no equivalent
 * field found in Crossref's grant schema). Unlike DataCite, {@code
 * contributions[].by.entityType} for persons has the same generator-driven gap (see {@code
 * DataCiteToSkgIfMapper}'s javadoc) but investigator-affiliation organisations and grant
 * beneficiaries do get {@code entityType: "organisation"} set correctly.
 */
@ApplicationScoped
public class CrossrefToSkgIfMapper {

    private static final String ORCID_HTTPS_PREFIX = "https://orcid.org/";
    private static final String ORCID_HTTP_PREFIX = "http://orcid.org/";
    private static final String ORCID_BASE_URL = "https://orcid.org/";
    private static final String ROR_BASE_URL = "https://ror.org/";
    private static final String CROSSREF_TYPES_BASE_URL = "https://api.crossref.org/types/";

    private final LocalIdentifiers localIdentifiers;
    private final CrossrefJournalDoiResolver journalDoiResolver;

    public CrossrefToSkgIfMapper(LocalIdentifiers localIdentifiers, CrossrefJournalDoiResolver journalDoiResolver) {
        this.localIdentifiers = localIdentifiers;
        this.journalDoiResolver = journalDoiResolver;
    }

    public Product toProduct(CrossrefWork work) {
        return toProduct(work, null);
    }

    /**
     * Overload accepting venue metadata parsed from Crossref's XML transform endpoint ({@code
     * application/vnd.crossref.unixsd+xml} - see {@code CrossrefVenueMetadataXmlParser}), fetched
     * only for chapter-in-a-book or paper-in-proceedings records ({@link
     * CrossrefTypeMapping#isXmlVenueEnrichable}). Lets {@link #venue} build an accurate Venue -
     * real container title/DOI/ISBN instead of the ambiguous {@code container-title[0]} - when
     * available; behaves exactly like the single-arg overload when {@code venueMetadata} is
     * {@code null} (e.g. the XML fetch failed, or this isn't an enrichable record).
     */
    public Product toProduct(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        Objects.requireNonNull(work.doi, "Crossref record has no DOI");

        return new Product()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi))
                .productType(CrossrefTypeMapping.productType(work.type))
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme("doi").value(work.doi)))
                .titles(titles(work))
                .abstracts(abstracts(work))
                .topics(topics(work))
                .contributions(contributions(work))
                .manifestations(List.of(manifestation(work, venueMetadata)))
                .funding(funding(work))
                .relatedProducts(relatedProducts(work));
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
     */
    public Grant toGrant(CrossrefWork work) {
        Objects.requireNonNull(work.doi, "Crossref record has no DOI");

        List<CrossrefProject> projects = work.project != null ? work.project : List.of();
        CrossrefProject primaryProject = projects.isEmpty() ? null : projects.get(0);
        CrossrefFunding primaryFunding = primaryProject != null && primaryProject.funding != null
                && !primaryProject.funding.isEmpty() ? primaryProject.funding.get(0) : null;

        return new Grant()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi))
                .entityType(Grant.EntityTypeEnum.GRANT)
                .identifiers(List.of(new GrantLiteAllOfIdentifiers().scheme("doi").value(work.doi)))
                .titles(grantTitles(projects))
                .abstracts(grantAbstracts(projects))
                .grantNumber(work.award)
                .fundingAgency(grantFundingAgency(work.doi, primaryFunding, work.funder))
                .fundingStream(primaryFunding != null ? primaryFunding.scheme : null)
                .fundedAmount(fundedAmount(primaryProject, primaryFunding))
                .currency(currency(primaryProject, primaryFunding))
                .duration(duration(primaryProject))
                .website(website(work))
                .contributions(grantContributions(work.doi, projects))
                .beneficiaries(grantBeneficiaries(work.doi, projects));
    }

    private Map<String, List<String>> titles(CrossrefWork work) {
        if (work.title == null || work.title.isEmpty()) {
            return null;
        }
        List<String> values = work.title.stream().filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    /**
     * Crossref's {@code abstract} is a single JATS-XML-tagged string (e.g. {@code
     * <jats:p>...</jats:p>}), not plain text like DataCite's - this strips the tags rather than
     * attempting to preserve any structure, since SKG-IF's {@code abstracts} field is plain text.
     */
    private Map<String, List<String>> abstracts(CrossrefWork work) {
        if (work.abstractText == null) {
            return null;
        }
        String stripped = work.abstractText.replaceAll("<[^>]+>", "").trim();
        return stripped.isEmpty() ? null : Map.of("en", List.of(stripped));
    }

    private List<ProductAllOfTopics> topics(CrossrefWork work) {
        if (work.subject == null || work.subject.isEmpty()) {
            return null;
        }
        List<ProductAllOfTopics> topics = new ArrayList<>();
        for (String subject : work.subject) {
            if (subject == null) {
                continue;
            }
            // Crossref subjects (Sci-Val controlled vocabulary) have no external identifier
            // system behind them, so this is always an otf id - same as DataCite subjects.
            ProductAllOfTerm term = new ProductAllOfTerm()
                    .localIdentifier(otf(work.doi, subject))
                    .entityType(ProductAllOfTerm.EntityTypeEnum.TOPIC)
                    .labels(Map.of("en", subject));
            topics.add(new ProductAllOfTopics().term(term));
        }
        return topics.isEmpty() ? null : topics;
    }

    private List<ProductContribution> contributions(CrossrefWork work) {
        List<ProductContribution> contributions = new ArrayList<>();
        int rank = 1;
        if (work.author != null) {
            for (CrossrefContributor author : work.author) {
                contributions.add(new ProductContribution()
                        .by(personRef(work.doi, author.given, author.family, author.orcid))
                        .declaredAffiliations(affiliations(work.doi, author.affiliation))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.AUTHOR));
            }
        }
        if (work.editor != null) {
            for (CrossrefContributor editor : work.editor) {
                contributions.add(new ProductContribution()
                        .by(personRef(work.doi, editor.given, editor.family, editor.orcid))
                        .declaredAffiliations(affiliations(work.doi, editor.affiliation))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.EDITOR));
            }
        }
        if (work.publisher != null) {
            contributions.add(new ProductContribution()
                    .by(organisationRef(work.doi, work.publisher))
                    .rank(rank++)
                    .role(ProductContribution.RoleEnum.PUBLISHER));
        }
        return contributions.isEmpty() ? null : contributions;
    }

    private ProductContributionBy personRef(String doi, String given, String family, String rawOrcid) {
        String bareOrcid = bareOrcid(rawOrcid);
        String name = displayName(given, family);
        ProductContributionBy by = new ProductContributionBy()
                .localIdentifier(bareOrcid != null ? ORCID_BASE_URL + bareOrcid : otf(doi, name))
                .name(name)
                .givenName(given)
                .familyName(family);
        if (bareOrcid != null) {
            by.identifiers(List.of(new AgentAllOfIdentifiers().scheme("orcid").value(bareOrcid)));
        }
        return by;
    }

    /**
     * Crossref's top-level {@code publisher} is a bare string with no external identifier
     * system behind it, so - like the {@code hosting_data_source} use of the same field - this
     * always gets an otf id.
     */
    private ProductContributionBy organisationRef(String doi, String name) {
        return new ProductContributionBy()
                .localIdentifier(otf(doi, name))
                .name(name);
    }

    /** Crossref's ORCID field is already a full URL (http or https) - normalize both to bare. */
    private String bareOrcid(String orcidUrl) {
        if (orcidUrl == null) {
            return null;
        }
        if (orcidUrl.startsWith(ORCID_HTTPS_PREFIX)) {
            return orcidUrl.substring(ORCID_HTTPS_PREFIX.length());
        }
        if (orcidUrl.startsWith(ORCID_HTTP_PREFIX)) {
            return orcidUrl.substring(ORCID_HTTP_PREFIX.length());
        }
        return orcidUrl;
    }

    private String displayName(String given, String family) {
        if (given == null) {
            return family;
        }
        if (family == null) {
            return given;
        }
        return given + " " + family;
    }

    /**
     * Crossref author/editor affiliations are usually name-only, but some publishers (e.g. APS)
     * do assert a ROR on them directly - same occasional-ROR situation as DataCite creator
     * affiliations, so this checks for one before falling back to an otf id.
     */
    private List<ProductAllOfRelevantOrganisations> affiliations(String doi, List<CrossrefAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        List<ProductAllOfRelevantOrganisations> result = new ArrayList<>();
        for (CrossrefAffiliation affiliation : affiliations) {
            if (affiliation.name == null) {
                continue;
            }
            String ror = firstRor(affiliation.id);
            ProductAllOfRelevantOrganisations org = new ProductAllOfRelevantOrganisations()
                    .localIdentifier(ror != null ? ROR_BASE_URL + ror : otf(doi, affiliation.name))
                    .name(affiliation.name)
                    .entityType(ProductAllOfRelevantOrganisations.EntityTypeEnum.ORGANISATION);
            if (ror != null) {
                org.identifiers(List.of(new AgentAllOfIdentifiers().scheme("ror").value(ror)));
            }
            result.add(org);
        }
        return result.isEmpty() ? null : result;
    }

    private String firstRor(List<CrossrefIdEntry> ids) {
        if (ids == null) {
            return null;
        }
        return ids.stream()
                .filter(entry -> "ROR".equalsIgnoreCase(entry.idType) && entry.id != null)
                .map(entry -> stripRorUrl(entry.id))
                .findFirst()
                .orElse(null);
    }

    private String stripRorUrl(String ror) {
        return ror.startsWith(ROR_BASE_URL) ? ror.substring(ROR_BASE_URL.length()) : ror;
    }

    private ProductManifestation manifestation(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        return new ProductManifestation()
                .type(manifestationType(work))
                .dates(dates(work))
                .accessRights(accessRights(work))
                .licence(licence(work))
                .biblio(biblio(work, venueMetadata));
    }

    private ProductManifestationType manifestationType(CrossrefWork work) {
        return work.type != null
                ? new ProductManifestationType()
                        .propertyClass(CROSSREF_TYPES_BASE_URL + work.type)
                        .definedIn(CROSSREF_TYPES_BASE_URL)
                        .labels(Map.of("en", work.type))
                : null;
    }

    private ProductManifestationDates dates(CrossrefWork work) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = false;
        any |= addDateItem(dates, "creation", work.created);
        any |= addDateItem(dates, "deposit", work.deposited);
        any |= addDateItem(dates, "access", work.posted);
        any |= addDateItem(dates, "acceptance", work.accepted);
        any |= addDateItem(dates, "publication", work.publishedPrint);
        any |= addDateItem(dates, "publication", work.publishedOnline);
        any |= addDateItem(dates, "publication", work.issued);
        if (work.updateTo != null) {
            for (CrossrefUpdateTo update : work.updateTo) {
                // "correction"/"retraction" are the only type values Crossref's own docs give
                // as examples (no exhaustive enum is published) - any other value is ignored.
                if ("correction".equals(update.type)) {
                    any |= addDateItem(dates, "correction", update.updated);
                } else if ("retraction".equals(update.type)) {
                    any |= addDateItem(dates, "retraction", update.updated);
                }
            }
        }
        return any ? dates : null;
    }

    private boolean addDateItem(ProductManifestationDates dates, String type, CrossrefDate date) {
        if (date == null) {
            return false;
        }
        String iso = date.toIsoDate();
        if (iso == null) {
            return false;
        }
        switch (type) {
            case "creation" -> dates.addCreationItem(iso);
            case "deposit" -> dates.addDepositItem(iso);
            case "access" -> dates.addAccessItem(iso);
            case "acceptance" -> dates.addAcceptanceItem(iso);
            case "publication" -> dates.addPublicationItem(iso);
            case "correction" -> dates.addCorrectionItem(iso);
            case "retraction" -> dates.addRetractionItem(iso);
            default -> {
                return false;
            }
        }
        return true;
    }

    private ProductManifestationAccessRights accessRights(CrossrefWork work) {
        if (work.license == null || work.license.isEmpty()) {
            return null;
        }
        boolean open = work.license.stream().anyMatch(this::isOpenLicence);
        return new ProductManifestationAccessRights()
                .status(open ? ProductManifestationAccessRights.StatusEnum.OPEN : null);
    }

    private boolean isOpenLicence(CrossrefLicense licence) {
        return licence.url != null && licence.url.contains("creativecommons.org");
    }

    private String licence(CrossrefWork work) {
        if (work.license == null || work.license.isEmpty()) {
            return null;
        }
        return work.license.get(0).url;
    }

    private ProductManifestationBiblio biblio(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        if (work.publisher == null && work.containerTitle == null && work.issue == null
                && work.volume == null && work.page == null && venueMetadata == null) {
            return null;
        }
        // The REST JSON's `volume` is the product's own volume/issue number (e.g. a journal
        // volume); a book chapter's or proceedings paper's REST JSON commonly has neither that
        // nor a series volume, but the XML transform's `.../volume` (e.g. an LNCS series volume
        // number, or a recurring proceedings series volume) fills that gap when present.
        String volume = work.volume != null ? work.volume
                : venueMetadata != null ? venueMetadata.volume() : null;
        ProductManifestationBiblio biblio = new ProductManifestationBiblio()
                .issue(work.issue)
                .volume(volume)
                .pages(pages(work.page))
                .in(venue(work, venueMetadata));
        if (work.publisher != null) {
            biblio.hostingDataSource(hostingDataSource(work));
        }
        return biblio;
    }

    private ProductManifestationBiblioPages pages(String page) {
        if (page == null || page.isBlank()) {
            return null;
        }
        String[] parts = page.split("-", 2);
        ProductManifestationBiblioPages pages = new ProductManifestationBiblioPages().first(parts[0].trim());
        if (parts.length > 1) {
            pages.last(parts[1].trim());
        }
        return pages;
    }

    /**
     * A new capability vs. the DataCite mapper, which never populates {@code biblio.in} at all -
     * Crossref's {@code container-title}/{@code ISSN} give a clean SKG-IF Venue.
     *
     * <p>For chapter-in-a-book or paper-in-proceedings records, {@code container-title[]} can
     * hold more than one entry (e.g. a book or proceedings that's part of a series: {@code
     * ["<series name>", "<actual title>"]}) with no way to tell them apart from the REST JSON
     * alone - see {@code mapsVenueFromFirstContainerTitleEntryWhenNoBookMetadataAvailable}'s
     * golden-tested fallback below. When {@code venueMetadata} (parsed from Crossref's XML
     * transform endpoint - see {@code CrossrefVenueMetadataXmlParser}) is present, it takes
     * precedence: the container's own DOI becomes a real {@code local_identifier} (rather than an
     * otf id, when Crossref recorded one) and {@code identifiers[]} gains {@code doi} and {@code
     * isbn} entries alongside any series {@code issn}.
     *
     * <p>Otherwise (the plain journal-article case, or any other non-XML-enrichable type), {@link
     * CrossrefJournalDoiResolver} is tried: many journals themselves have a real Crossref {@code
     * type: "journal"} DOI, resolved live via their ISSN. When found, it's used the same way as
     * the XML-enriched container DOI above (real {@code local_identifier}, {@code doi} entry
     * alongside {@code issn}); when not (no journal-level DOI registered, or the lookup fails),
     * falls back to the {@code container-title[0]}+otf-id+ISSN-only heuristic.
     */
    private ProductManifestationBiblioIn venue(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        if (venueMetadata != null && venueMetadata.containerTitle() != null) {
            return venueFromXmlMetadata(work.doi, venueMetadata);
        }
        if (work.containerTitle == null || work.containerTitle.isEmpty() || work.containerTitle.get(0) == null) {
            return null;
        }
        String name = work.containerTitle.get(0);
        List<String> issns = work.issn != null
                ? work.issn.stream().filter(Objects::nonNull).toList()
                : List.of();
        String journalDoi = issns.isEmpty() ? null : journalDoiResolver.resolveJournalDoi(issns).orElse(null);

        ProductManifestationBiblioIn venue = new ProductManifestationBiblioIn()
                .localIdentifier(journalDoi != null ? localIdentifiers.toFullLocalIdentifier(journalDoi)
                        : otf(work.doi, name))
                .entityType("venue")
                .name(name);

        List<VenueLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        if (journalDoi != null) {
            identifiers.add(new VenueLiteAllOfIdentifiers().scheme("doi").value(journalDoi));
        }
        issns.forEach(issn -> identifiers.add(new VenueLiteAllOfIdentifiers().scheme("issn").value(issn)));
        if (!identifiers.isEmpty()) {
            venue.identifiers(identifiers);
        }
        return venue;
    }

    private ProductManifestationBiblioIn venueFromXmlMetadata(String doi, CrossrefVenueMetadata venueMetadata) {
        String name = venueMetadata.containerTitle();
        String containerDoi = venueMetadata.containerDoi();
        ProductManifestationBiblioIn venue = new ProductManifestationBiblioIn()
                .localIdentifier(containerDoi != null ? localIdentifiers.toFullLocalIdentifier(containerDoi)
                        : otf(doi, name))
                .entityType("venue")
                .name(name);

        List<VenueLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        if (containerDoi != null) {
            identifiers.add(new VenueLiteAllOfIdentifiers().scheme("doi").value(containerDoi));
        }
        if (venueMetadata.seriesIssns() != null) {
            venueMetadata.seriesIssns().stream()
                    .filter(Objects::nonNull)
                    .forEach(issn -> identifiers.add(new VenueLiteAllOfIdentifiers().scheme("issn").value(issn)));
        }
        if (venueMetadata.isbns() != null) {
            venueMetadata.isbns().stream()
                    .filter(Objects::nonNull)
                    .forEach(isbn -> identifiers.add(new VenueLiteAllOfIdentifiers().scheme("isbn").value(isbn)));
        }
        if (!identifiers.isEmpty()) {
            venue.identifiers(identifiers);
        }
        return venue;
    }

    /**
     * Crossref's own {@code publisher} field is the closest generic equivalent of "where this
     * record is hosted" - same otf-id convention as DataCite's hostingDataSource.
     */
    private ProductManifestationBiblioHostingDataSource hostingDataSource(CrossrefWork work) {
        return new ProductManifestationBiblioHostingDataSource()
                .localIdentifier(otf(work.doi, work.publisher))
                .entityType(ProductManifestationBiblioHostingDataSource.EntityTypeEnum.DATASOURCE)
                .name(work.publisher);
    }

    private List<ProductAllOfFunding> funding(CrossrefWork work) {
        if (work.funder == null || work.funder.isEmpty()) {
            return null;
        }
        List<ProductAllOfFunding> result = new ArrayList<>();
        for (CrossrefFunder funder : work.funder) {
            if (funder.name == null) {
                continue;
            }
            List<String> awards = funder.award != null ? funder.award : List.of();
            if (awards.isEmpty()) {
                result.add(fundingEntry(work.doi, funder, null));
            } else {
                for (String award : awards) {
                    result.add(fundingEntry(work.doi, funder, award));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private ProductAllOfFunding fundingEntry(String doi, CrossrefFunder funder, String awardNumber) {
        String label = awardNumber != null ? awardNumber : funder.name;
        return new ProductAllOfFunding()
                .localIdentifier(otf(doi, label))
                .entityType(ProductAllOfFunding.EntityTypeEnum.GRANT)
                .grantNumber(awardNumber)
                .fundingAgency(fundingAgencyOrg(doi, funder));
    }

    /**
     * The Funder Registry DOI plays the role DataCite's ROR-typed {@code funderIdentifier} plays
     * - note the identifier scheme emitted here is {@code doi}, not {@code ror}, since that's
     * genuinely what Crossref gives (a funder's Funder Registry DOI, not its ROR).
     */
    private Organisation fundingAgencyOrg(String doi, CrossrefFunder funder) {
        String funderDoi = funderDoi(funder);
        Organisation agency = new Organisation()
                .localIdentifier(funderDoi != null ? localIdentifiers.toFullLocalIdentifier(funderDoi) : otf(doi, funder.name))
                .name(funder.name)
                .entityType(Organisation.EntityTypeEnum.ORGANISATION);
        if (funderDoi != null) {
            agency.identifiers(List.of(new AgentAllOfIdentifiers().scheme("doi").value(funderDoi)));
        }
        return agency;
    }

    /**
     * A top-level {@code work.funder[]} entry carries the Funder Registry DOI directly as {@code
     * DOI}; a grant record's {@code project[].funding[].funder} only has it inside {@code id[]}
     * (verified live against a real grant record) - check both.
     */
    private String funderDoi(CrossrefFunder funder) {
        if (funder.doi != null) {
            return funder.doi;
        }
        if (funder.id == null) {
            return null;
        }
        return funder.id.stream()
                .filter(entry -> "DOI".equalsIgnoreCase(entry.idType) && entry.id != null)
                .map(entry -> entry.id)
                .findFirst()
                .orElse(null);
    }

    /**
     * An "on-the-fly" identifier per the SKG-IF Entity.local_identifier convention, for entities
     * with no stable identifier of their own - same convention as {@code DataCiteToSkgIfMapper},
     * built from the owning record's DOI so it's deterministic.
     */
    private String otf(String doi, String label) {
        return "otf___" + slug(doi) + "___" + slug(label);
    }

    private String slug(String text) {
        if (text == null) {
            return "unknown";
        }
        String slug = text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            return "unknown";
        }
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    /**
     * Unlike DataCite (where citations live in {@code relatedIdentifiers[relationType=Cites]}),
     * Crossref's citation list is {@code reference[]} - verified live that plain works commonly
     * carry an empty {@code relation} map even with a populated {@code reference[]}, so that
     * hashmap (documented for other relation types like is-preprint-of/is-supplement-to) is not
     * a reliable source of "this work cites DOI X" and isn't used here. Only entries the
     * depositing publisher asserted a DOI for get a real identifier; free-text-only references
     * still get an entry with an otf id (same fallback DataCite's {@code relatedByType} uses for
     * non-DOI related identifiers) rather than being dropped.
     */
    private ProductsRelated relatedProducts(CrossrefWork work) {
        if (work.reference == null || work.reference.isEmpty()) {
            return null;
        }
        List<ProductsRelatedCitesInner> cites = new ArrayList<>();
        for (CrossrefReference reference : work.reference) {
            if (reference.doi != null) {
                // Full https://doi.org/... URL, consistent with how this API identifies its own
                // products and every other DOI-identified entity.
                cites.add(new ProductsRelatedCitesInner()
                        .localIdentifier(localIdentifiers.toFullLocalIdentifier(reference.doi))
                        .entityType("product")
                        .identifiers(List.of(new EntityIdentifiersInner().scheme("doi").value(reference.doi))));
                continue;
            }
            String label = reference.unstructured != null ? reference.unstructured : reference.key;
            cites.add(new ProductsRelatedCitesInner()
                    .localIdentifier(otf(work.doi, label))
                    .entityType("product"));
        }
        return cites.isEmpty() ? null : new ProductsRelated().cites(cites);
    }

    private Map<String, List<String>> grantTitles(List<CrossrefProject> projects) {
        List<String> values = projects.stream()
                .filter(p -> p.projectTitle != null)
                .flatMap(p -> p.projectTitle.stream())
                .map(t -> t.title)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    private Map<String, List<String>> grantAbstracts(List<CrossrefProject> projects) {
        List<String> values = projects.stream()
                .filter(p -> p.projectDescription != null)
                .flatMap(p -> p.projectDescription.stream())
                .map(d -> d.description)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    private Organisation grantFundingAgency(String doi, CrossrefFunding primaryFunding, List<CrossrefFunder> topLevelFunders) {
        CrossrefFunder funder = primaryFunding != null ? primaryFunding.funder : null;
        if (funder == null && topLevelFunders != null && !topLevelFunders.isEmpty()) {
            funder = topLevelFunders.get(0);
        }
        if (funder == null || funder.name == null) {
            return null;
        }
        return fundingAgencyOrg(doi, funder);
    }

    private Integer fundedAmount(CrossrefProject project, CrossrefFunding funding) {
        CrossrefAmount amount = awardAmount(project, funding);
        return amount != null && amount.amount != null ? amount.amount.intValue() : null;
    }

    private String currency(CrossrefProject project, CrossrefFunding funding) {
        CrossrefAmount amount = awardAmount(project, funding);
        return amount != null ? amount.currency : null;
    }

    private CrossrefAmount awardAmount(CrossrefProject project, CrossrefFunding funding) {
        if (funding != null && funding.awardAmount != null) {
            return funding.awardAmount;
        }
        return project != null ? project.awardAmount : null;
    }

    private GrantAllOfDuration duration(CrossrefProject project) {
        if (project == null) {
            return null;
        }
        String start = project.awardStart != null ? project.awardStart.toIsoDate() : null;
        String end = project.awardEnd != null ? project.awardEnd.toIsoDate() : null;
        return start == null && end == null ? null : new GrantAllOfDuration().start(start).end(end);
    }

    private String website(CrossrefWork work) {
        return work.resource != null && work.resource.primary != null ? work.resource.primary.url : null;
    }

    private List<GrantAllOfContributions> grantContributions(String doi, List<CrossrefProject> projects) {
        List<GrantAllOfContributions> result = new ArrayList<>();
        for (CrossrefProject project : projects) {
            if (project.leadInvestigator != null) {
                for (CrossrefInvestigator investigator : project.leadInvestigator) {
                    result.add(investigatorContribution(doi, investigator, GrantAllOfContributions.RolesEnum.LEAD_APPLICANT));
                }
            }
            if (project.investigator != null) {
                for (CrossrefInvestigator investigator : project.investigator) {
                    result.add(investigatorContribution(doi, investigator, GrantAllOfContributions.RolesEnum.CO_APPLICANT));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private GrantAllOfContributions investigatorContribution(String doi, CrossrefInvestigator investigator,
            GrantAllOfContributions.RolesEnum role) {
        String bareOrcid = bareOrcid(investigator.orcid);
        String name = displayName(investigator.given, investigator.family);
        GrantContributionBy by = new GrantContributionBy()
                .localIdentifier(bareOrcid != null ? ORCID_BASE_URL + bareOrcid : otf(doi, name))
                .name(name)
                .givenName(investigator.given)
                .familyName(investigator.family);
        if (bareOrcid != null) {
            by.identifiers(List.of(new AgentAllOfIdentifiers().scheme("orcid").value(bareOrcid)));
        }
        return new GrantAllOfContributions()
                .by(by)
                .declaredAffiliations(grantAffiliations(doi, investigator.affiliation))
                .roles(List.of(role));
    }

    private List<GrantAllOfBeneficiaries> grantAffiliations(String doi, List<CrossrefAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        List<GrantAllOfBeneficiaries> result = new ArrayList<>();
        for (CrossrefAffiliation affiliation : affiliations) {
            if (affiliation.name == null) {
                continue;
            }
            String ror = firstRor(affiliation.id);
            GrantAllOfBeneficiaries org = new GrantAllOfBeneficiaries()
                    .localIdentifier(ror != null ? ROR_BASE_URL + ror : otf(doi, affiliation.name))
                    .name(affiliation.name)
                    .entityType(GrantAllOfBeneficiaries.EntityTypeEnum.ORGANISATION);
            if (ror != null) {
                org.identifiers(List.of(new AgentAllOfIdentifiers().scheme("ror").value(ror)));
            }
            result.add(org);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Crossref grant records have no separate "organisational contributor" concept the way
     * DataCite Awards do - this reuses the investigators' own declared affiliations as
     * beneficiaries (deduped by name), the closest available analogue. Same documented judgment
     * call as {@code DataCiteToSkgIfMapper#grantBeneficiaries}.
     */
    private List<GrantAllOfBeneficiaries> grantBeneficiaries(String doi, List<CrossrefProject> projects) {
        Map<String, CrossrefAffiliation> byName = new LinkedHashMap<>();
        for (CrossrefProject project : projects) {
            collectAffiliations(byName, project.leadInvestigator);
            collectAffiliations(byName, project.investigator);
        }
        return grantAffiliations(doi, new ArrayList<>(byName.values()));
    }

    private void collectAffiliations(Map<String, CrossrefAffiliation> byName, List<CrossrefInvestigator> investigators) {
        if (investigators == null) {
            return;
        }
        for (CrossrefInvestigator investigator : investigators) {
            if (investigator.affiliation == null) {
                continue;
            }
            for (CrossrefAffiliation affiliation : investigator.affiliation) {
                if (affiliation.name != null) {
                    byName.putIfAbsent(affiliation.name, affiliation);
                }
            }
        }
    }
}
