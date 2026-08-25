package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One {@code contributors[]} entry of a DataCite record.
 *
 * @param name            the contributor's full name as deposited
 * @param givenName       the contributor's given name(s)
 * @param familyName      the contributor's family name
 * @param nameType        whether the contributor is a person or an organisation
 * @param contributorType the contributor's role (e.g. {@code Editor})
 * @param nameIdentifiers the contributor's identifiers, such as an ORCID
 * @param affiliation     the contributor's affiliations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteContributor(
        @Nullable String name,
        @Nullable String givenName,
        @Nullable String familyName,
        @Nullable String nameType,
        @Nullable String contributorType,
        @Nullable List<DataCiteNameIdentifier> nameIdentifiers,
        @Nullable List<DataCiteAffiliation> affiliation) {
}
