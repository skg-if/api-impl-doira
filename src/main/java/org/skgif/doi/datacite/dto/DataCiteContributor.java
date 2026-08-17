package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

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
