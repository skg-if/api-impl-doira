package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Top-level Jackson DTO for an entire DataCite work/DOI record - necessarily has many
// independent optional fields; splitting it wouldn't help since Jackson needs one class
// matching the source JSON's shape.
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteAttributes(
        String doi,
        List<Title> titles,
        List<DataCiteCreator> creators,
        List<DataCiteContributor> contributors,
        String publisher,
        Integer publicationYear,
        List<DataCiteSubject> subjects,
        List<DataCiteDate> dates,
        // System-generated record-lifecycle timestamps, distinct from the researcher-asserted
        // dates[] array above - used by the mapper only as a fallback when dates[] has no
        // Created/Submitted/Updated/Issued entry (see DataCiteManifestationMapper#dates).
        String created,
        String registered,
        String published,
        String updated,
        String language,
        Types types,
        List<DataCiteRights> rightsList,
        List<DataCiteDescription> descriptions,
        List<DataCiteRelatedIdentifier> relatedIdentifiers,
        List<DataCiteFundingReference> fundingReferences,
        String version,
        String url) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Title(
            String title,
            String lang) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Types(
            String resourceTypeGeneral,
            String resourceType) {
    }
}
