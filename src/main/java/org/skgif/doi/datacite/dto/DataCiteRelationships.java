package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The DOI's DataCite {@code relationships} block. Only {@code client} is modeled - the DataCite
 * client (e.g. {@code "inist.esrf"}) that registered the DOI, used to namespace the JSON-LD
 * {@code @base} per client (see {@code JsonLdResponses#contextBaseFor}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteRelationships {

    public ClientRelationship client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientRelationship {
        public ClientData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientData {
        public String id;
    }
}
