package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDescription {

    public String description;
    public String descriptionType;
    public String lang;
}
