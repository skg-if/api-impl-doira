package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteAttributes {

    public String doi;
    public List<Title> titles;
    public List<DataCiteCreator> creators;
    public List<DataCiteContributor> contributors;
    public String publisher;
    public Integer publicationYear;
    public List<DataCiteSubject> subjects;
    public List<DataCiteDate> dates;
    // System-generated record-lifecycle timestamps, distinct from the researcher-asserted
    // dates[] array above - used by the mapper only as a fallback when dates[] has no
    // Created/Submitted/Updated/Issued entry (see DataCiteToSkgIfMapper#dates).
    public String created;
    public String registered;
    public String published;
    public String updated;
    public String language;
    public Types types;
    public List<DataCiteRights> rightsList;
    public List<DataCiteDescription> descriptions;
    public List<DataCiteRelatedIdentifier> relatedIdentifiers;
    public List<DataCiteFundingReference> fundingReferences;
    public String version;
    public String url;

    // Field name mirrors the DataCite JSON key ("title") it's deserialized from, same as every
    // other DTO field in this class - not a naming smell.
    @SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Title {
        public String title;
        public String lang;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Types {
        public String resourceTypeGeneral;
        public String resourceType;
    }
}
