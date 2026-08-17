package org.skgif.doi.medra.mapper;

import java.util.ArrayList;
import java.util.List;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.medra.dto.MedraContributor;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.util.EntityRefs;

/**
 * Maps a mEDRA ONIX-for-DOI record's contributors onto {@code Product.contributions}. Split out
 * of {@code MedraToSkgIfMapper} to keep that class down to orchestration.
 */
final class MedraContributionMapper {

    private MedraContributionMapper() {
    }

    static List<ProductContribution> contributions(MedraWork work) {
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
     *
     * @param doi the owning record's DOI, used to build a deterministic otf id
     * @param contributor the ONIX-for-DOI contributor to derive a person reference from
     * @return the mapped PersonLite, or null if contributor carries none of the three name shapes
     */
    private static ProductContributionBy personRef(String doi, MedraContributor contributor) {
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
        return EntityRefs.personRef(doi, name, givenName, familyName, null, null);
    }

    private static String displayName(String given, String family) {
        if (given == null) {
            return family;
        }
        if (family == null) {
            return given;
        }
        return given + " " + family;
    }

    /**
     * Splits an ONIX {@code PersonNameInverted} string (e.g. "Fragneto, Giovanna") into {family, given}.
     *
     * @param inverted an inverted-order name string ("Family, Given"), or null
     * @return an array of {family, given}, or null if inverted is null
     */
    private static String[] splitInverted(String inverted) {
        if (inverted == null) {
            return null;
        }
        String[] parts = inverted.split(",\\s*", 2);
        return parts.length == 2 ? new String[] {parts[0].trim(), parts[1].trim()}
                : new String[] {parts[0].trim(), null};
    }
}
