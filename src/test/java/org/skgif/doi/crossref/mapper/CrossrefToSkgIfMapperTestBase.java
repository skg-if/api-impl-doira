package org.skgif.doi.crossref.mapper;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Shared fixture-loading/mapping setup for {@link CrossrefToSkgIfMapper} test classes - each
 * concrete subclass only adds its own {@code @Test} methods. {@link #crossrefClient} is left
 * entirely unstubbed by default, so the journal-DOI resolver degrades to {@code Optional.empty()}
 * for every ISSN unless a subclass stubs {@code listWorks(...)} itself (see
 * {@code CrossrefToSkgIfMapperVenueTest} for the resolver-hit/resolver-failure paths that do).
 */
abstract class CrossrefToSkgIfMapperTestBase {

    /** Used to read the JSON fixture files this test maps. */
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /** Mocked Crossref REST client, unstubbed by default (see class javadoc above). */
    protected final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    /** The mapper under test. */
    protected final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    protected Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    protected CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return Objects.requireNonNull(response.message(), "Fixture has no message block: " + resourceName);
        }
    }
}
