package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

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
        String name,
        String givenName,
        String familyName,
        String nameType,
        String contributorType,
        List<DataCiteNameIdentifier> nameIdentifiers,
        List<DataCiteAffiliation> affiliation) {
}
