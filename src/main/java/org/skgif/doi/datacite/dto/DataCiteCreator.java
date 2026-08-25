package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
        @Nullable String name,
        @Nullable String givenName,
        @Nullable String familyName,
        @Nullable String nameType,
        @Nullable List<DataCiteNameIdentifier> nameIdentifiers,
        @Nullable List<DataCiteAffiliation> affiliation) {
}
