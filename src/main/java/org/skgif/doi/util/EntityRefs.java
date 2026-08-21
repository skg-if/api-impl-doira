package org.skgif.doi.util;

import java.util.List;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.spec.IdentifierScheme;

/**
 * Builds SKG-IF entity-reference objects (an organisation/person/data-source "by"/"beneficiary"
 * reference, not the full entity record) shared by every provider's mapper - byte-identical
 * object-construction logic that was previously duplicated once per mapper class. Each caller
 * still does its own DTO-specific field extraction (e.g. finding a ROR/ORCID/DOI on its own
 * differently-shaped DTO); this class only builds the resulting reference object from already-
 * extracted primitive values, so it stays fully static/stateless.
 */
public final class EntityRefs {

    private EntityRefs() {
    }

    /**
     * An organisation reference (a publisher, an affiliation, a grant beneficiary...) identified
     * by ROR when known, otherwise an otf id. Each caller resolves its own DTO's ROR field into
     * a bare ROR value first (schemes/field names for "this affiliation has a ROR" differ per
     * provider); this only builds the resulting reference object.
     *
     * @param doi     the owning record's DOI, used to build a deterministic otf id when bareRor is
     *                null
     * @param name    the organisation's name
     * @param bareRor the organisation's bare ROR id (no {@code https://ror.org/} prefix), or null
     *                if none is known
     * @return an Organisation reference, ROR-identified when bareRor is present, otf-identified
     *         otherwise
     */
    public static Organisation organisationRef(String doi, String name, String bareRor) {
        Organisation org = new Organisation()
                .localIdentifier(bareRor != null ?
                        ExternalIdentifierUrls.ROR_BASE_URL + bareRor :
                        MapperTextUtils.otf(doi, name))
                .name(name)
                .entityType(EntityTypes.ORGANISATION.value());
        if (bareRor != null) {
            org.identifiers(List.of(new AgentAllOfIdentifiers().scheme(IdentifierScheme.ROR.value()).value(bareRor)));
        }
        return org;
    }

    /**
     * A funding agency reference: ROR-identified when known (same convention as {@link
     * #organisationRef(String, String, String)}), else identified by a Funder Registry DOI when
     * one is available, else an otf id. Each caller resolves its own DTO's ROR/DOI fields (and,
     * for DOI, the real dereferenceable local_identifier via its own {@code LocalIdentifiers})
     * first; this only builds the resulting reference object.
     *
     * @param doi                the owning record's DOI, used to build a deterministic otf id when neither
     *                           bareRor nor bareDoiValue is present
     * @param name               the funding agency's name
     * @param bareRor            the funder's bare ROR id, or null if none is known
     * @param doiLocalIdentifier the funder's DOI resolved to a full local_identifier (via {@code
     *     LocalIdentifiers#toFullLocalIdentifier}), or null if bareDoiValue is null
     * @param bareDoiValue       the funder's bare Funder Registry DOI, or null if none is known
     * @return an Organisation reference, ROR-identified, else DOI-identified, else otf-identified
     */
    // Each parameter maps 1:1 to a value the caller already resolved from its own DTO/service -
    // same reasoning as personRef's suppression above.
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public static Organisation organisationRef(String doi, String name, String bareRor, String doiLocalIdentifier,
            String bareDoiValue) {
        if (bareRor != null) {
            return organisationRef(doi, name, bareRor);
        }
        Organisation agency = new Organisation()
                .localIdentifier(bareDoiValue != null ? doiLocalIdentifier : MapperTextUtils.otf(doi, name))
                .name(name)
                .entityType(EntityTypes.ORGANISATION.value());
        if (bareDoiValue != null) {
            agency.identifiers(
                    List.of(new AgentAllOfIdentifiers().scheme(IdentifierScheme.DOI.value()).value(bareDoiValue)));
        }
        return agency;
    }

    /**
     * A person reference (an author, editor, investigator...) identified by ORCID when known,
     * otherwise an otf id. Each caller resolves its own DTO's ORCID field(s) into a bare ORCID
     * value and the identifiers list first (the shape of "does this contributor have an ORCID"
     * differs per provider); this only builds the resulting reference object.
     *
     * @param doi              the owning record's DOI, used to build a deterministic otf id when bareOrcid is
     *                         null
     * @param name             the person's display name
     * @param givenName        the person's given name, or null
     * @param familyName       the person's family name, or null
     * @param bareOrcid        the person's bare ORCID id (no {@code https://orcid.org/} prefix), or null
     *                         if none is known
     * @param orcidIdentifiers the person's {@code identifiers[]} entry for this ORCID, or null if
     *                         bareOrcid is null
     * @return a PersonLite reference, ORCID-identified when bareOrcid is present, otf-identified
     *         otherwise
     */
    // Each parameter maps 1:1 to a PersonLite field the caller already extracted from its own
    // DTO - bundling them into a container object would need a new DTO for no real clarity gain
    // over five named, individually-documented parameters (same call as CrossrefClient's).
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public static PersonLite personRef(String doi, String name, String givenName, String familyName,
            String bareOrcid, List<PersonLiteAllOfIdentifiers> orcidIdentifiers) {
        PersonLite by = new PersonLite()
                .localIdentifier(bareOrcid != null ?
                        ExternalIdentifierUrls.ORCID_BASE_URL + bareOrcid :
                        MapperTextUtils.otf(doi, name))
                .name(name)
                .givenName(givenName)
                .familyName(familyName)
                .entityType(EntityTypes.PERSON.value());
        if (orcidIdentifiers != null) {
            by.identifiers(orcidIdentifiers);
        }
        return by;
    }

    /**
     * A record's publisher, mapped as the closest generic equivalent of "where this record is
     * hosted." A bare publisher string has no external identifier system behind it, so this
     * always gets an otf id.
     *
     * @param doi  the owning record's DOI, used to build a deterministic otf id
     * @param name the publisher's name
     * @return a DataSourceLite for name, with an otf local_identifier
     */
    public static ProductManifestationBiblioHostingDataSource hostingDataSource(String doi, String name) {
        return new DataSourceLite()
                .localIdentifier(MapperTextUtils.otf(doi, name))
                .entityType(DataSourceLite.EntityTypeEnum.DATASOURCE)
                .name(name);
    }
}
