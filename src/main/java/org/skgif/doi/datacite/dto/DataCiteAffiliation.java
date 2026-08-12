package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = DataCiteAffiliationDeserializer.class)
public class DataCiteAffiliation {

    public String name;
    public String affiliationIdentifier;
    public String affiliationIdentifierScheme;
}
