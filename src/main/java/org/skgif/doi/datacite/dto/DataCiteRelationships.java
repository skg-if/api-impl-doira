package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The DOI's DataCite {@code relationships} block. Only {@code client} is modeled - the DataCite
 * client (e.g. {@code "inist.esrf"}) that registered the DOI, used to namespace the JSON-LD
 * {@code @base} per client (see {@code JsonLdContextBase#contextBaseFor}).
 *
 * @param client the DataCite client relationship that registered the DOI
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteRelationships(
        ClientRelationship client) {

    /**
     * The {@code relationships.client} wrapper around the registering client's data.
     *
     * @param data the registering client's data member
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientRelationship(
            ClientData data) {
    }

    /**
     * The {@code relationships.client.data} member, whose id names the registering client.
     *
     * @param id the DataCite client id (e.g. {@code inist.esrf})
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientData(
            String id) {
    }
}
