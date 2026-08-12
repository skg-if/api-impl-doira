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
    public String language;
    public Types types;
    public List<DataCiteRights> rightsList;
    public List<DataCiteDescription> descriptions;
    public List<DataCiteRelatedIdentifier> relatedIdentifiers;
    public List<DataCiteFundingReference> fundingReferences;
    public String version;
    public String url;

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
