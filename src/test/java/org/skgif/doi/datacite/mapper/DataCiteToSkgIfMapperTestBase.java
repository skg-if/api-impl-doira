package org.skgif.doi.datacite.mapper;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
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
            requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            DataCiteDoiData data = requireNonNull(response.data(), "Fixture has no data block: " +
                    resourceName);
            return requireNonNull(data.attributes(), "Fixture has no attributes block: " + resourceName);
        }
    }
}
