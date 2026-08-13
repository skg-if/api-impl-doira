package org.skgif.doi.medra.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.generated.model.ProductManifestationBiblioIn;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.medra.dto.MedraContributor;
import org.skgif.doi.medra.dto.MedraTitle;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Maps a mEDRA ONIX-for-DOI record ({@link MedraWork}, built by {@code MedraOnixXmlParser}) onto
 * the SKG-IF {@code Product} entity. Mirrors {@code CrossrefToSkgIfMapper}/{@code
 * DataCiteToSkgIfMapper}'s conventions (otf ids, full-URL local_identifiers for DOIs) where the
 * source format supports them.
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
public class MedraToSkgIfMapper {

    private static final String ONIX_SERIAL_ARTICLE_SPEC_URL =
            "https://www.medra.org/stdoc/ONIX_DOI_Serial_Article_2.0_v.2.pdf";

    private final LocalIdentifiers localIdentifiers;

    public MedraToSkgIfMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    public Product toProduct(MedraWork work) {
        Objects.requireNonNull(work.doi(), "mEDRA record has no DOI");

        return new Product()
                .localIdentifier(localIdentifiers.toFullLocalIdentifier(work.doi()))
                .productType(Product.ProductTypeEnum.LITERATURE)
                .identifiers(List.of(new ProductAllOfIdentifiers().scheme("doi").value(work.doi())))
                .titles(titles(work))
                .abstracts(abstracts(work))
                .contributions(contributions(work))
                .manifestations(List.of(manifestation(work)));
    }

    /**
     * Groups {@code ContentItem}-level titles by their {@code language} attribute (defaulting to
     * {@code "en"} when absent, same convention as {@code CrossrefToSkgIfMapper.titles}), keeping
     * every {@code TitleType} for that language in document order - mEDRA gives no field to
     * distinguish "full" vs. "abbreviated" title once inside {@code Product.titles} anyway.
     */
    private Map<String, List<String>> titles(MedraWork work) {
        if (work.titles() == null || work.titles().isEmpty()) {
            return null;
        }
        Map<String, List<String>> titles = new LinkedHashMap<>();
        for (MedraTitle title : work.titles()) {
            String language = title.language() != null ? title.language() : "en";
            titles.computeIfAbsent(language, key -> new ArrayList<>()).add(title.text());
        }
        return titles.isEmpty() ? null : titles;
    }

    private Map<String, List<String>> abstracts(MedraWork work) {
        return work.abstractText() == null ? null : Map.of("en", List.of(work.abstractText()));
    }

    private List<ProductContribution> contributions(MedraWork work) {
        if (work.contributors() == null || work.contributors().isEmpty()) {
            return null;
        }
        List<ProductContribution> contributions = new ArrayList<>();
        int rank = 1;
        for (MedraContributor contributor : work.contributors()) {
            ProductContributionBy by = personRef(work.doi(), contributor);
            if (by == null) {
                continue;
            }
            contributions.add(new ProductContribution()
                    .by(by)
                    .rank(rank++)
                    .role("A01".equals(contributor.role()) ? ProductContribution.RoleEnum.AUTHOR : null));
        }
        return contributions.isEmpty() ? null : contributions;
    }

    /**
     * ONIX-for-DOI contributors carry exactly one of three mutually exclusive name shapes, tried
     * here in this precedence order: (1) {@code NamesBeforeKey}+{@code KeyNames} - the most
     * structured source, wins even when the other shapes are also present (confirmed live on
     * `10.19276/plinius.2019.01004`, which has all four fields together); (2) {@code PersonName}
     * (already a natural-order composed string, used as-is) optionally paired with {@code
     * PersonNameInverted} to also derive given/family; (3) {@code PersonNameInverted} alone
     * (confirmed live on `10.12919/sapere.2018.04.3` - no {@code PersonName} sibling at all),
     * split into given/family and re-composed in natural order for {@code name}, for consistency
     * with how every other provider stores it. A bare {@code PersonName} with no {@code
     * PersonNameInverted} (e.g. "Cotte M.") isn't safely splittable, so given/family stay unset
     * rather than guessed at. No ORCID (or any other person identifier) was observed on any
     * contributor in the fixtures examined, so the local_identifier is always an otf id.
     */
    private ProductContributionBy personRef(String doi, MedraContributor contributor) {
        String givenName;
        String familyName;
        String name;
        if (contributor.namesBeforeKey() != null && contributor.keyNames() != null) {
            givenName = contributor.namesBeforeKey();
            familyName = contributor.keyNames();
            name = displayName(givenName, familyName);
        } else if (contributor.personName() != null) {
            name = contributor.personName();
            String[] split = splitInverted(contributor.personNameInverted());
            familyName = split != null ? split[0] : null;
            givenName = split != null ? split[1] : null;
        } else if (contributor.personNameInverted() != null) {
            String[] split = splitInverted(contributor.personNameInverted());
            familyName = split[0];
            givenName = split[1];
            name = displayName(givenName, familyName);
        } else {
            return null;
        }
        return new PersonLite()
                .localIdentifier(otf(doi, name))
                .name(name)
                .givenName(givenName)
                .familyName(familyName)
                .entityType("person");
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

    /** Splits an ONIX {@code PersonNameInverted} string (e.g. "Fragneto, Giovanna") into {family, given}. */
    private String[] splitInverted(String inverted) {
        if (inverted == null) {
            return null;
        }
        String[] parts = inverted.split(",\\s*", 2);
        return parts.length == 2 ? new String[] {parts[0].trim(), parts[1].trim()}
                : new String[] {parts[0].trim(), null};
    }

    private ProductManifestation manifestation(MedraWork work) {
        return new ProductManifestation()
                .type(manifestationType())
                .dates(dates(work))
                .biblio(biblio(work));
    }

    private ProductManifestationType manifestationType() {
        return new ProductManifestationType()
                .definedIn(ONIX_SERIAL_ARTICLE_SPEC_URL)
                .labels(Map.of("en", "journal-article"));
    }

    private ProductManifestationDates dates(MedraWork work) {
        String iso = isoDate(work.publicationDate());
        return iso == null ? null : new ProductManifestationDates().addPublicationItem(iso);
    }

    /**
     * mEDRA's {@code PublicationDate} is a bare digit string with no format marker (unlike {@code
     * JournalIssueDate}, which carries an explicit {@code DateFormat} code and has no clean
     * SKG-IF home of its own - see SKG_IF_DOI_MAPPING_DATES.md) - length alone distinguishes
     * year-only ("2019"), year+month ("202103"), and full-date ("20210813") forms, confirmed
     * across all 6 fixtures.
     */
    private String isoDate(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.length()) {
            case 4 -> raw;
            case 6 -> raw.substring(0, 4) + "-" + raw.substring(4, 6);
            case 8 -> raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
            default -> null;
        };
    }

    private ProductManifestationBiblio biblio(MedraWork work) {
        String hostingName = work.publisherName() != null ? work.publisherName() : work.registrantName();
        if (work.journalTitle() == null && hostingName == null) {
            return null;
        }
        ProductManifestationBiblio biblio = new ProductManifestationBiblio().in(venue(work));
        if (hostingName != null) {
            biblio.hostingDataSource(hostingDataSource(work.doi(), hostingName));
        }
        return biblio;
    }

    /**
     * mEDRA gives no journal-DOI equivalent to Crossref's {@code CrossrefJournalDoiResolver} - the
     * venue's {@code local_identifier} is always an otf id, backed only by the journal/series'
     * own ISSN(s) as its {@code identifiers[]}.
     */
    private ProductManifestationBiblioIn venue(MedraWork work) {
        if (work.journalTitle() == null) {
            return null;
        }
        VenueLite venue = new VenueLite()
                .localIdentifier(otf(work.doi(), work.journalTitle()))
                .entityType("venue")
                .name(work.journalTitle());
        if (work.issns() != null && !work.issns().isEmpty()) {
            venue.identifiers(work.issns().stream()
                    .map(issn -> new VenueLiteAllOfIdentifiers().scheme("issn").value(issn))
                    .toList());
        }
        return venue;
    }

    /** mEDRA's {@code PublisherName} (falling back to {@code RegistrantName}) has no external ID system. */
    private ProductManifestationBiblioHostingDataSource hostingDataSource(String doi, String name) {
        return new DataSourceLite()
                .localIdentifier(otf(doi, name))
                .entityType(DataSourceLite.EntityTypeEnum.DATASOURCE)
                .name(name);
    }

    /**
     * An "on-the-fly" identifier per the SKG-IF Entity.local_identifier convention - same
     * convention as {@code CrossrefToSkgIfMapper}/{@code DataCiteToSkgIfMapper}.
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
}
