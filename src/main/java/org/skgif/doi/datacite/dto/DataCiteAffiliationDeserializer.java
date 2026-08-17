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
        if (node.isTextual()) {
            return new DataCiteAffiliation(node.asText(), null, null);
        } else if (node.isObject()) {
            return new DataCiteAffiliation(
                    node.path("name").asText(null),
                    node.path("affiliationIdentifier").asText(null),
                    node.path("affiliationIdentifierScheme").asText(null));
        }
        return null;
    }
}
