package org.skgif.doi.datacite.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Shared fixture-loading/mapping setup for {@link DataCiteToSkgIfMapper} test classes - each
 * concrete subclass only adds its own {@code @Test} methods.
 */
abstract class DataCiteToSkgIfMapperTestBase {

    /** Used to read the JSON fixture files this test maps. */
    protected final ObjectMapper objectMapper = new ObjectMapper();
    /** The mapper under test. */
    protected final DataCiteToSkgIfMapper mapper = new DataCiteToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    protected Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    protected DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data().attributes();
        }
    }
}
