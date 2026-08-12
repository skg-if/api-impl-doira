package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteRights {

    public String rights;
    public String rightsUri;
    public String rightsIdentifier;
}
