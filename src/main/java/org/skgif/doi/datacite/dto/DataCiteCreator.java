package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One {@code creators[]} entry of a DataCite record.
 *
 * @param name            the creator's full name as deposited
 * @param givenName       the creator's given name(s)
 * @param familyName      the creator's family name
 * @param nameType        whether the creator is a person or an organisation
 * @param nameIdentifiers the creator's identifiers, such as an ORCID
 * @param affiliation     the creator's affiliations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteCreator(
        String name,
        String givenName,
        String familyName,
        String nameType,
        List<DataCiteNameIdentifier> nameIdentifiers,
        List<DataCiteAffiliation> affiliation) {
}
