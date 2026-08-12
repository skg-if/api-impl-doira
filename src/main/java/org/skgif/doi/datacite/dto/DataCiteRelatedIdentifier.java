package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteRelatedIdentifier {

    public String relatedIdentifier;
    public String relatedIdentifierType;
    public String relationType;
}
