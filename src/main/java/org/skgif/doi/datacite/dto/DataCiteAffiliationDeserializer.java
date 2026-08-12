package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * DataCite's {@code affiliation} array elements are, depending on how the depositor submitted
 * the record, either plain strings (just the affiliation name) or objects with {@code name}/
 * {@code affiliationIdentifier}/{@code affiliationIdentifierScheme}. Jackson can't bind both
 * shapes to the same POJO without help, hence this deserializer.
 */
class DataCiteAffiliationDeserializer extends JsonDeserializer<DataCiteAffiliation> {

    @Override
    public DataCiteAffiliation deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.readValueAsTree();
        DataCiteAffiliation affiliation = new DataCiteAffiliation();
        if (node.isTextual()) {
            affiliation.name = node.asText();
        } else if (node.isObject()) {
            affiliation.name = node.path("name").asText(null);
            affiliation.affiliationIdentifier = node.path("affiliationIdentifier").asText(null);
            affiliation.affiliationIdentifierScheme = node.path("affiliationIdentifierScheme").asText(null);
        }
        return affiliation;
    }
}
