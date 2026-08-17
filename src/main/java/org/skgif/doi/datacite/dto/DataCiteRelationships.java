package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The DOI's DataCite {@code relationships} block. Only {@code client} is modeled - the DataCite
 * client (e.g. {@code "inist.esrf"}) that registered the DOI, used to namespace the JSON-LD
 * {@code @base} per client (see {@code JsonLdResponses#contextBaseFor}).
 *
 * @param client the DataCite client relationship that registered the DOI
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteRelationships(ClientRelationship client) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientRelationship(ClientData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientData(String id) {
    }
}
