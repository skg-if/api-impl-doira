package org.skgif.doi.datacite.mapper;

import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteAffiliation;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteContributor;
import org.skgif.doi.datacite.dto.DataCiteCreator;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.datacite.dto.DataCiteDescription;
import org.skgif.doi.datacite.dto.DataCiteFundingReference;
import org.skgif.doi.datacite.dto.DataCiteNameIdentifier;
import org.skgif.doi.datacite.dto.DataCiteRelatedIdentifier;
import org.skgif.doi.datacite.dto.DataCiteRights;
import org.skgif.doi.datacite.dto.DataCiteSubject;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.EntityIdentifiersInner;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantAllOfBeneficiaries;
import org.skgif.doi.generated.model.GrantAllOfContributions;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.GrantContributionBy;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.GrantLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
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
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.generated.model.ProductsRelated;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.generated.model.Topic;
import org.skgif.doi.util.LocalIdentifiers;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps a DataCite DOI record ({@code attributes}) onto either the SKG-IF {@code Product}
 * entity ({@link #toProduct}) or, for DataCite's {@code resourceTypeGeneral: "Award"} records,
 * the SKG-IF {@code Grant} entity ({@link #toGrant} - see {@link ResourceTypeMapping#isAward}).
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

    private static final String ORCID_BASE_URL = "https://orcid.org/";
    private static final String ROR_BASE_URL = "https://ror.org/";
    private static final String DATACITE_RESOURCE_TYPE_SCHEMA_URL =
            "https://schema.datacite.org/meta/kernel-4.7/include/datacite-resourceType-v4.xsd";
    private static final Pattern DOI_SHAPE = Pattern.compile("10\\.\\d{4,9}/.+");

    private static final Map<String, String> DATACITE_DATE_TYPE_TO_SKGIF = Map.of(
            "Accepted", "acceptance",
            "Available", "embargo",
            "Collected", "collected",
            "Copyrighted", "copyright",
            "Created", "creation",
            "Issued", "publication",
            "Submitted", "deposit",
            "Updated", "modified",
            "Valid", "validity",
            "Withdrawn", "retraction");

    private final LocalIdentifiers localIdentifiers;

    public DataCiteToSkgIfMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    public Product toProduct(DataCiteAttributes attributes) {
        Objects.requireNonNull(attributes.doi, "DataCite record has no DOI");

        Product product = new Product()
                // Full https://doi.org/... form, consistent with every other entity in this
                // output (Person -> ORCID URL, Organisation -> ROR URL): use the full external
                // identifier URL as local_identifier whenever we have a real one.
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(attributes.doi))
                .productType(ResourceTypeMapping.productType(resourceTypeGeneral(attributes)))
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme("doi").value(attributes.doi)))
                .titles(titles(attributes))
                .abstracts(abstracts(attributes))
                .topics(topics(attributes))
                .contributions(contributions(attributes))
                .manifestations(List.of(manifestation(attributes)))
                .funding(funding(attributes))
                .relatedProducts(relatedProducts(attributes));
        return product;
    }

    /**
     * Maps a DataCite Award record ({@code resourceTypeGeneral: "Award"}) onto the SKG-IF
     * {@code Grant} entity. The DataCite Award schema has no dedicated "who funds this" field,
     * so the real-world convention this follows (confirmed against live Award DOIs from
     * multiple DataCite members) is: the first creator carrying a ROR identifier is the
     * funding body itself; every other creator plus all contributors are the grant's
     * contributions; organisational contributors are also listed as beneficiaries.
     */
    public Grant toGrant(DataCiteAttributes attributes) {
        Objects.requireNonNull(attributes.doi, "DataCite record has no DOI");

        List<DataCiteCreator> creators = attributes.creators != null ? attributes.creators : List.of();
        List<DataCiteContributor> contributors = attributes.contributors != null ? attributes.contributors : List.of();
        Optional<DataCiteCreator> fundingAgencyCreator =
                creators.stream().filter(c -> firstRor(c.nameIdentifiers) != null).findFirst();

        return new Grant()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(attributes.doi))
                .entityType(Grant.EntityTypeEnum.GRANT)
                .identifiers(List.of(new GrantLiteAllOfIdentifiers().scheme("doi").value(attributes.doi)))
                .titles(titles(attributes))
                .abstracts(abstracts(attributes))
                .fundingAgency(grantFundingAgency(attributes.doi, fundingAgencyCreator, attributes.publisher))
                .contributions(grantContributions(attributes.doi, creators, contributors, fundingAgencyCreator))
                .beneficiaries(grantBeneficiaries(attributes.doi, contributors));
    }

    private Map<String, List<String>> titles(DataCiteAttributes attributes) {
        if (attributes.titles == null || attributes.titles.isEmpty()) {
            return null;
        }
        List<String> values = attributes.titles.stream().map(t -> t.title).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    private Map<String, List<String>> abstracts(DataCiteAttributes attributes) {
        if (attributes.descriptions == null) {
            return null;
        }
        List<String> values = attributes.descriptions.stream()
                .filter(d -> "Abstract".equals(d.descriptionType))
                .map(d -> d.description)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    private List<ProductAllOfTopics> topics(DataCiteAttributes attributes) {
        if (attributes.subjects == null || attributes.subjects.isEmpty()) {
            return null;
        }
        List<ProductAllOfTopics> topics = new ArrayList<>();
        for (DataCiteSubject subject : attributes.subjects) {
            if (subject.subject == null) {
                continue;
            }
            String lang = subject.lang != null ? subject.lang : "none";
            // DataCite subjects have no external identifier system behind them, so this is
            // always an otf id - there's nothing more stable to hang it off.
            ProductAllOfTerm term = new Topic()
                    .localIdentifier(otf(attributes.doi, subject.subject))
                    .entityType(Topic.EntityTypeEnum.TOPIC)
                    .labels(Map.of(lang, subject.subject));
            topics.add(new ProductAllOfTopics().term(term));
        }
        return topics.isEmpty() ? null : topics;
    }

    private List<ProductContribution> contributions(DataCiteAttributes attributes) {
        List<ProductContribution> contributions = new ArrayList<>();
        int rank = 1;
        if (attributes.creators != null) {
            for (DataCiteCreator creator : attributes.creators) {
                contributions.add(new ProductContribution()
                        .by(personRef(attributes.doi, creator.name, creator.givenName, creator.familyName,
                                creator.nameIdentifiers))
                        .declaredAffiliations(affiliations(attributes.doi, creator.affiliation))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.AUTHOR));
            }
        }
        if (attributes.contributors != null) {
            for (DataCiteContributor contributor : attributes.contributors) {
                contributions.add(new ProductContribution()
                        .by(personRef(attributes.doi, contributor.name, contributor.givenName, contributor.familyName,
                                contributor.nameIdentifiers))
                        .declaredAffiliations(affiliations(attributes.doi, contributor.affiliation))
                        .rank(rank++)
                        .role(contributorRole(contributor.contributorType)));
            }
        }
        if (attributes.publisher != null) {
            contributions.add(new ProductContribution()
                    .by(organisationRef(attributes.doi, attributes.publisher))
                    .rank(rank++)
                    .role(ProductContribution.RoleEnum.PUBLISHER));
        }
        return contributions.isEmpty() ? null : contributions;
    }

    private ProductContribution.RoleEnum contributorRole(String dataCiteContributorType) {
        if ("Editor".equals(dataCiteContributorType)) {
            return ProductContribution.RoleEnum.EDITOR;
        }
        return ProductContribution.RoleEnum.AUTHOR;
    }

    private ProductContributionBy personRef(String doi, String name, String givenName, String familyName,
            List<DataCiteNameIdentifier> nameIdentifiers) {
        String orcid = firstOrcid(nameIdentifiers);
        PersonLite by = new PersonLite()
                .localIdentifier(orcid != null ? ORCID_BASE_URL + orcid : otf(doi, name))
                .name(name)
                .givenName(givenName)
                .familyName(familyName)
                .entityType("person");
        List<PersonLiteAllOfIdentifiers> identifiers = orcidIdentifiers(nameIdentifiers);
        if (identifiers != null) {
            by.identifiers(identifiers);
        }
        return by;
    }

    /**
     * DataCite's top-level {@code publisher} is a bare string with no external identifier
     * system behind it, so - like the {@code hosting_data_source} use of the same field - this
     * always gets an otf id.
     */
    private ProductContributionBy organisationRef(String doi, String name) {
        return new Organisation()
                .localIdentifier(otf(doi, name))
                .name(name)
                .entityType("organisation");
    }

    private String firstOrcid(List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return null;
        }
        return nameIdentifiers.stream()
                .filter(ni -> "ORCID".equalsIgnoreCase(ni.nameIdentifierScheme) && ni.nameIdentifier != null)
                .map(ni -> ni.nameIdentifier.startsWith(ORCID_BASE_URL)
                        ? ni.nameIdentifier.substring(ORCID_BASE_URL.length())
                        : ni.nameIdentifier)
                .findFirst()
                .orElse(null);
    }

    private List<PersonLiteAllOfIdentifiers> orcidIdentifiers(
            List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null || nameIdentifiers.isEmpty()) {
            return null;
        }
        List<PersonLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        for (DataCiteNameIdentifier ni : nameIdentifiers) {
            if (!"ORCID".equalsIgnoreCase(ni.nameIdentifierScheme)) {
                continue;
            }
            String orcid = ni.nameIdentifier;
            if (orcid != null && orcid.startsWith(ORCID_BASE_URL)) {
                orcid = orcid.substring(ORCID_BASE_URL.length());
            }
            identifiers.add(new PersonLiteAllOfIdentifiers()
                    .scheme("orcid")
                    .value(orcid));
        }
        return identifiers.isEmpty() ? null : identifiers;
    }

    private List<ProductAllOfRelevantOrganisations> affiliations(String doi, List<DataCiteAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        List<ProductAllOfRelevantOrganisations> result = new ArrayList<>();
        for (DataCiteAffiliation affiliation : affiliations) {
            if (affiliation.name == null) {
                continue;
            }
            boolean hasRor = affiliation.affiliationIdentifier != null
                    && "ROR".equalsIgnoreCase(affiliation.affiliationIdentifierScheme);
            Organisation org = new Organisation()
                    .localIdentifier(hasRor
                            ? ROR_BASE_URL + stripRorUrl(affiliation.affiliationIdentifier)
                            : otf(doi, affiliation.name))
                    .name(affiliation.name)
                    .entityType("organisation");
            if (hasRor) {
                org.identifiers(List.of(new AgentAllOfIdentifiers()
                        .scheme("ror")
                        .value(stripRorUrl(affiliation.affiliationIdentifier))));
            }
            result.add(org);
        }
        return result.isEmpty() ? null : result;
    }

    private ProductManifestation manifestation(DataCiteAttributes attributes) {
        ProductManifestation manifestation = new ProductManifestation()
                .type(manifestationType(attributes))
                .dates(dates(attributes))
                .accessRights(accessRights(attributes))
                .licence(licence(attributes))
                .version(attributes.version)
                .biblio(biblio(attributes));
        return manifestation;
    }

    private ProductManifestationType manifestationType(DataCiteAttributes attributes) {
        String resourceType = resourceTypeGeneral(attributes);
        if (resourceType == null) {
            return null;
        }
        return new ProductManifestationType()
                .definedIn(DATACITE_RESOURCE_TYPE_SCHEMA_URL)
                .labels(Map.of("en", resourceType));
    }

    private String resourceTypeGeneral(DataCiteAttributes attributes) {
        return attributes.types != null ? attributes.types.resourceTypeGeneral : null;
    }

    private ProductManifestationDates dates(DataCiteAttributes attributes) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = false;
        if (attributes.dates != null) {
            for (DataCiteDate date : attributes.dates) {
                String skgIfDateType = DATACITE_DATE_TYPE_TO_SKGIF.get(date.dateType);
                if (skgIfDateType == null || date.date == null) {
                    continue;
                }
                // An `Available` date only signals a genuine embargo when it differs (at day
                // granularity) from every other date already known for this record - if it
                // coincides with e.g. `Issued` or the top-level `created` timestamp, that's just
                // "published and immediately available," not an embargo end date, so it's dropped
                // rather than emitted anywhere.
                if ("embargo".equals(skgIfDateType)
                        && otherRecordDays(attributes, date).contains(normalizeDay(date.date))) {
                    continue;
                }
                any = true;
                switch (skgIfDateType) {
                    case "acceptance" -> dates.addAcceptanceItem(date.date);
                    case "collected" -> dates.addCollectedItem(date.date);
                    case "copyright" -> dates.addCopyrightItem(date.date);
                    case "creation" -> dates.addCreationItem(date.date);
                    case "publication" -> dates.addPublicationItem(date.date);
                    case "deposit" -> dates.addDepositItem(date.date);
                    case "modified" -> dates.addModifiedItem(date.date);
                    case "validity" -> dates.addValidityItem(date.date);
                    case "retraction" -> dates.addRetractionItem(date.date);
                    case "embargo" -> dates.addEmbargoItem(date.date);
                    default -> any = false;
                }
            }
        }
        // Fall back to DataCite's system-generated record timestamps when the corresponding
        // dateType is absent from dates[] - which in practice is the norm, not the exception:
        // Created/Submitted/Updated essentially never appear there (see SKG_IF_DOI_MAPPING_DATES.md).
        // An explicit dates[] entry always takes precedence since these only fire when the
        // getter is still null.
        if (dates.getCreation() == null && attributes.created != null) {
            dates.addCreationItem(attributes.created);
            any = true;
        }
        if (dates.getDeposit() == null && attributes.registered != null) {
            dates.addDepositItem(attributes.registered);
            any = true;
        }
        if (dates.getModified() == null && attributes.updated != null) {
            dates.addModifiedItem(attributes.updated);
            any = true;
        }
        if (dates.getPublication() == null && attributes.published != null) {
            dates.addPublicationItem(attributes.published);
            any = true;
        }
        return any ? dates : null;
    }

    /**
     * Day-normalized (YYYY-MM-DD) set of every other date already known for this record - the
     * rest of {@code attributes.dates} plus the top-level fallback timestamps - used to tell a
     * genuine embargo end date apart from an {@code Available} entry that merely restates another
     * date on the record.
     */
    private Set<String> otherRecordDays(DataCiteAttributes attributes, DataCiteDate excluding) {
        Set<String> days = new HashSet<>();
        for (DataCiteDate date : attributes.dates) {
            if (date != excluding && date.date != null) {
                days.add(normalizeDay(date.date));
            }
        }
        if (attributes.created != null) {
            days.add(normalizeDay(attributes.created));
        }
        if (attributes.registered != null) {
            days.add(normalizeDay(attributes.registered));
        }
        if (attributes.updated != null) {
            days.add(normalizeDay(attributes.updated));
        }
        if (attributes.published != null) {
            days.add(normalizeDay(attributes.published));
        }
        return days;
    }

    /**
     * Truncates a DataCite date string to its YYYY-MM-DD day, so a full timestamp
     * ({@code "2024-05-07T10:07:27.000Z"}) compares equal to a date-only value for the same day
     * ({@code "2024-05-07"}). Partial values (e.g. a bare year {@code "2028"}) are left as-is.
     */
    private String normalizeDay(String date) {
        return date.length() >= 10 ? date.substring(0, 10) : date;
    }

    private ProductManifestationAccessRights accessRights(DataCiteAttributes attributes) {
        if (attributes.rightsList == null || attributes.rightsList.isEmpty()) {
            return null;
        }
        boolean open = attributes.rightsList.stream().anyMatch(this::isOpenLicence);
        return new ProductManifestationAccessRights()
                .status(open ? ProductManifestationAccessRights.StatusEnum.OPEN : null);
    }

    private boolean isOpenLicence(DataCiteRights rights) {
        return rights.rightsUri != null && rights.rightsUri.contains("creativecommons.org");
    }

    private String licence(DataCiteAttributes attributes) {
        if (attributes.rightsList == null || attributes.rightsList.isEmpty()) {
            return null;
        }
        return attributes.rightsList.get(0).rightsUri;
    }

    private ProductManifestationBiblio biblio(DataCiteAttributes attributes) {
        if (attributes.publisher == null) {
            return null;
        }
        return new ProductManifestationBiblio().hostingDataSource(hostingDataSource(attributes));
    }

    /**
     * DataCite's own {@code publisher} field is the closest generic equivalent of "where this
     * record is hosted" - unlike an organisation's ROR, there's no external identifier system
     * for an arbitrary publisher string, so this always gets an otf id.
     */
    private ProductManifestationBiblioHostingDataSource hostingDataSource(DataCiteAttributes attributes) {
        return new DataSourceLite()
                .localIdentifier(otf(attributes.doi, attributes.publisher))
                .entityType(DataSourceLite.EntityTypeEnum.DATASOURCE)
                .name(attributes.publisher);
    }

    private List<ProductAllOfFunding> funding(DataCiteAttributes attributes) {
        if (attributes.fundingReferences == null || attributes.fundingReferences.isEmpty()) {
            return null;
        }
        List<ProductAllOfFunding> result = new ArrayList<>();
        for (DataCiteFundingReference fundingReference : attributes.fundingReferences) {
            // DataCite funding references carry no stable identifier for the grant itself
            // (unlike the funder, which often has a ROR) - the award number/title is the
            // closest thing to a natural key, so that's what the otf id is built from.
            String label = fundingReference.awardNumber != null
                    ? fundingReference.awardNumber
                    : fundingReference.awardTitle;
            GrantLite grant = new GrantLite()
                    .localIdentifier(otf(attributes.doi, label))
                    .entityType(GrantLite.EntityTypeEnum.GRANT)
                    .grantNumber(fundingReference.awardNumber)
                    .titles(fundingReference.awardTitle != null ? Map.of("en", List.of(fundingReference.awardTitle)) : null)
                    .fundingAgency(fundingAgency(attributes.doi, fundingReference));
            result.add(grant);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * DataCite's {@code funderIdentifierType} controlled vocabulary has no literal {@code "DOI"}
     * value, but {@code "Crossref Funder ID"} (and occasionally other/unlabeled identifiers) are
     * themselves Funder Registry DOIs in practice - functionally identical to how Crossref's own
     * mapper treats {@code funder[].DOI}. Rather than special-casing that one type label, this
     * detects the DOI shape directly on the identifier value, so any DOI-shaped
     * funderIdentifier is used regardless of what (if anything) its type claims to be - verified
     * live against a real "Crossref Funder ID"-typed record (see
     * {@code datacite-thesis-crossref-funder-id-4342.json}). Non-DOI identifier types (GRID,
     * ISNI, Wikidata) still have no home here and fall back to an otf id.
     */
    private Organisation fundingAgency(String doi,
            DataCiteFundingReference fundingReference) {
        if (fundingReference.funderName == null) {
            return null;
        }
        boolean hasRor = fundingReference.funderIdentifier != null
                && "ROR".equalsIgnoreCase(fundingReference.funderIdentifierType);
        String funderDoi = hasRor ? null : extractDoi(fundingReference.funderIdentifier);
        Organisation agency = new Organisation()
                .localIdentifier(hasRor
                        ? ROR_BASE_URL + stripRorUrl(fundingReference.funderIdentifier)
                        : funderDoi != null
                                ? localIdentifiers.toFullLocalIdentifier(funderDoi)
                                : otf(doi, fundingReference.funderName))
                .name(fundingReference.funderName)
                .entityType("organisation");
        if (hasRor) {
            agency.identifiers(List.of(new AgentAllOfIdentifiers()
                    .scheme("ror")
                    .value(stripRorUrl(fundingReference.funderIdentifier))));
        } else if (funderDoi != null) {
            agency.identifiers(List.of(new AgentAllOfIdentifiers()
                    .scheme("doi")
                    .value(funderDoi)));
        }
        return agency;
    }

    /**
     * Strips a {@code https://doi.org/}/{@code http://doi.org/} prefix if present, then checks
     * the remainder against the DOI shape ({@code 10.<4-9 digits>/<suffix>}) - returns the bare
     * DOI, or {@code null} if the identifier isn't DOI-shaped at all.
     */
    private String extractDoi(String identifier) {
        if (identifier == null) {
            return null;
        }
        String candidate = identifier;
        if (candidate.startsWith("https://doi.org/")) {
            candidate = candidate.substring("https://doi.org/".length());
        } else if (candidate.startsWith("http://doi.org/")) {
            candidate = candidate.substring("http://doi.org/".length());
        }
        return DOI_SHAPE.matcher(candidate).matches() ? candidate : null;
    }

    private String stripRorUrl(String ror) {
        return ror.startsWith(ROR_BASE_URL) ? ror.substring(ROR_BASE_URL.length()) : ror;
    }

    private String firstRor(List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return null;
        }
        return nameIdentifiers.stream()
                .filter(ni -> "ROR".equalsIgnoreCase(ni.nameIdentifierScheme) && ni.nameIdentifier != null)
                .map(ni -> stripRorUrl(ni.nameIdentifier))
                .findFirst()
                .orElse(null);
    }

    private Organisation grantFundingAgency(String doi, Optional<DataCiteCreator> fundingAgencyCreator, String publisher) {
        if (fundingAgencyCreator.isPresent()) {
            DataCiteCreator creator = fundingAgencyCreator.get();
            String ror = firstRor(creator.nameIdentifiers);
            return new Organisation()
                    .localIdentifier(ROR_BASE_URL + ror)
                    .name(creator.name)
                    .entityType("organisation")
                    .identifiers(List.of(new AgentAllOfIdentifiers().scheme("ror").value(ror)));
        }
        // No ROR-bearing creator to identify the funder - fall back to the record's own
        // publisher, same convention used for Product.manifestations[].biblio.hosting_data_source.
        if (publisher == null) {
            return null;
        }
        return new Organisation()
                .localIdentifier(otf(doi, publisher))
                .name(publisher)
                .entityType("organisation");
    }

    private List<GrantAllOfContributions> grantContributions(String doi, List<DataCiteCreator> creators,
            List<DataCiteContributor> contributors, Optional<DataCiteCreator> fundingAgencyCreator) {
        List<GrantAllOfContributions> result = new ArrayList<>();
        for (DataCiteCreator creator : creators) {
            if (fundingAgencyCreator.isPresent() && fundingAgencyCreator.get() == creator) {
                continue;
            }
            boolean organizational = "Organizational".equals(creator.nameType);
            result.add(new GrantContribution()
                    .by(grantContributionBy(doi, creator.name, creator.givenName, creator.familyName,
                            creator.nameIdentifiers, organizational))
                    .declaredAffiliations(grantAffiliations(doi, creator.affiliation)));
        }
        for (DataCiteContributor contributor : contributors) {
            boolean organizational = "Organizational".equals(contributor.nameType);
            result.add(new GrantContribution()
                    .by(grantContributionBy(doi, contributor.name, contributor.givenName, contributor.familyName,
                            contributor.nameIdentifiers, organizational))
                    .declaredAffiliations(grantAffiliations(doi, contributor.affiliation)));
        }
        return result.isEmpty() ? null : result;
    }

    private GrantContributionBy grantContributionBy(String doi, String name, String givenName, String familyName,
            List<DataCiteNameIdentifier> nameIdentifiers, boolean organizational) {
        if (organizational) {
            String ror = firstRor(nameIdentifiers);
            Organisation by = new Organisation()
                    .localIdentifier(ror != null ? ROR_BASE_URL + ror : otf(doi, name))
                    .name(name)
                    .entityType("organisation");
            if (ror != null) {
                by.identifiers(List.of(new AgentAllOfIdentifiers().scheme("ror").value(ror)));
            }
            return by;
        }
        String orcid = firstOrcid(nameIdentifiers);
        PersonLite by = new PersonLite()
                .localIdentifier(orcid != null ? ORCID_BASE_URL + orcid : otf(doi, name))
                .name(name)
                .givenName(givenName)
                .familyName(familyName)
                .entityType("person");
        List<PersonLiteAllOfIdentifiers> identifiers = orcidIdentifiers(nameIdentifiers);
        if (identifiers != null) {
            by.identifiers(identifiers);
        }
        return by;
    }

    private List<GrantAllOfBeneficiaries> grantAffiliations(String doi, List<DataCiteAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        List<GrantAllOfBeneficiaries> result = new ArrayList<>();
        for (DataCiteAffiliation affiliation : affiliations) {
            if (affiliation.name == null) {
                continue;
            }
            boolean hasRor = affiliation.affiliationIdentifier != null
                    && "ROR".equalsIgnoreCase(affiliation.affiliationIdentifierScheme);
            Organisation org = new Organisation()
                    .localIdentifier(hasRor
                            ? ROR_BASE_URL + stripRorUrl(affiliation.affiliationIdentifier)
                            : otf(doi, affiliation.name))
                    .name(affiliation.name)
                    .entityType("organisation");
            if (hasRor) {
                org.identifiers(List.of(new AgentAllOfIdentifiers()
                        .scheme("ror")
                        .value(stripRorUrl(affiliation.affiliationIdentifier))));
            }
            result.add(org);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Organisational contributors (DataCite {@code nameType: "Organizational"}) are also listed
     * as the grant's beneficiaries, alongside appearing in {@code contributions} - both are
     * legitimate per the spec's own worked example (GraspOS: Brown University is both a
     * contribution's declared affiliation and a top-level beneficiary).
     */
    private List<GrantAllOfBeneficiaries> grantBeneficiaries(String doi, List<DataCiteContributor> contributors) {
        List<DataCiteAffiliation> organizationalContributors = new ArrayList<>();
        for (DataCiteContributor contributor : contributors) {
            if (!"Organizational".equals(contributor.nameType) || contributor.name == null) {
                continue;
            }
            DataCiteAffiliation asAffiliation = new DataCiteAffiliation();
            asAffiliation.name = contributor.name;
            String ror = firstRor(contributor.nameIdentifiers);
            if (ror != null) {
                asAffiliation.affiliationIdentifier = ROR_BASE_URL + ror;
                asAffiliation.affiliationIdentifierScheme = "ROR";
            }
            organizationalContributors.add(asAffiliation);
        }
        return grantAffiliations(doi, organizationalContributors);
    }

    /**
     * An "on-the-fly" identifier per the SKG-IF Entity.local_identifier convention, for entities
     * with no stable identifier of their own. Built from the owning product's DOI (rather than
     * e.g. a timestamp) so it's deterministic - repeated calls for the same DOI produce the same
     * id instead of a fresh one every time.
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

    private ProductsRelated relatedProducts(DataCiteAttributes attributes) {
        if (attributes.relatedIdentifiers == null || attributes.relatedIdentifiers.isEmpty()) {
            return null;
        }
        // DataCite's controlled vocabulary has both "Cites" and "References" for what SKG-IF
        // models as a single relation - real records use either (verified live against
        // 10.5281/zenodo.21914195, which has 12 "Cites" entries plus one separate "References"
        // entry) - both feed the same output array.
        List<ProductsRelatedCitesInner> cites = new ArrayList<>();
        cites.addAll(relatedByType(attributes, "Cites"));
        cites.addAll(relatedByType(attributes, "References"));
        List<ProductsRelatedCitesInner> citedBy = relatedByType(attributes, "IsCitedBy");
        // "IsSupplementTo" is a distinct DataCite relation type from "IsSupplementedBy" (the
        // inverse direction) - verified live against 10.5281/zenodo.21827103, which carries
        // both an "IsSupplementedBy" and, on a different related identifier, an "IsSupplementTo"
        // that must NOT be picked up here.
        List<ProductsRelatedCitesInner> isSupplementedBy = relatedByType(attributes, "IsSupplementedBy");
        List<ProductsRelatedCitesInner> isDocumentedBy = relatedByType(attributes, "IsDocumentedBy");
        List<ProductsRelatedCitesInner> isNewVersionOf = relatedByType(attributes, "IsNewVersionOf");
        List<ProductsRelatedCitesInner> isPartOf = relatedByType(attributes, "IsPartOf");
        if (cites.isEmpty() && citedBy.isEmpty() && isSupplementedBy.isEmpty() && isDocumentedBy.isEmpty()
                && isNewVersionOf.isEmpty() && isPartOf.isEmpty()) {
            return null;
        }
        ProductsRelated related = new ProductsRelated();
        if (!cites.isEmpty()) {
            related.cites(cites);
        }
        if (!isSupplementedBy.isEmpty()) {
            related.isSupplementedBy(isSupplementedBy);
        }
        if (!isDocumentedBy.isEmpty()) {
            related.isDocumentedBy(isDocumentedBy);
        }
        if (!isNewVersionOf.isEmpty()) {
            related.isNewVersionOf(isNewVersionOf);
        }
        if (!isPartOf.isEmpty()) {
            related.isPartOf(isPartOf);
        }
        return related;
    }

    private List<ProductsRelatedCitesInner> relatedByType(DataCiteAttributes attributes, String relationType) {
        List<ProductsRelatedCitesInner> result = new ArrayList<>();
        for (DataCiteRelatedIdentifier related : attributes.relatedIdentifiers) {
            if (!relationType.equals(related.relationType) || related.relatedIdentifier == null) {
                continue;
            }
            String scheme = related.relatedIdentifierType != null
                    ? related.relatedIdentifierType.toLowerCase()
                    : "url";
            // A related product with a DOI is identified by the full https://doi.org/... URL,
            // consistent with how this API identifies its own products; anything else falls
            // back to otf.
            String localIdentifier = "doi".equals(scheme)
                    ? localIdentifiers.toFullLocalIdentifier(related.relatedIdentifier)
                    : otf(attributes.doi, related.relatedIdentifier);
            result.add(new ProductsRelatedItem()
                    .localIdentifier(localIdentifier)
                    .entityType("product")
                    .identifiers(List.of(new EntityIdentifiersInner().scheme(scheme).value(related.relatedIdentifier))));
        }
        return result;
    }
}
