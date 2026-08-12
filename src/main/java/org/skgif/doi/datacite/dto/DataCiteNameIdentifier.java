package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteNameIdentifier {

    public String nameIdentifier;
    public String nameIdentifierScheme;
    public String schemeUri;
}
