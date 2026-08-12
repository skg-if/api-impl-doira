package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteFundingReference {

    public String funderName;
    public String funderIdentifier;
    public String funderIdentifierType;
    public String awardNumber;
    public String awardTitle;
    public String awardUri;
}
