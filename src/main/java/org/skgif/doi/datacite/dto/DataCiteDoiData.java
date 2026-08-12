package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiData {

    public String id;
    public String type;
    public DataCiteAttributes attributes;
}
