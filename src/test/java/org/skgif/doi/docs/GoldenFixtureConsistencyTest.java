package org.skgif.doi.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards against a new DataCite/Crossref/mEDRA source fixture being committed without the
 * corresponding golden JSON-LD output under {@code src/test/resources/expected/} - the mistake
 * this test exists to catch already slipped through once for {@code
 * datacite-dataset-multiple-crossref-funder-ids-15047595.json} before a golden test was added for
 * it. See {@code ProductsGoldenTest}/{@code GrantsGoldenTest} for how a golden pair is normally
 * produced and regenerated.
 */
class GoldenFixtureConsistencyTest {

    /** The repository's root directory. */
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath();
    /** Directory holding source fixtures and their expected golden outputs. */
    private static final Path FIXTURES_DIR = REPO_ROOT.resolve("src/test/resources");
    /** Directory holding the golden JSON-LD outputs, one per source fixture. */
    private static final Path EXPECTED_DIR = FIXTURES_DIR.resolve("expected");
    /** Matches a DataCite/Crossref/mEDRA source fixture file name. */
    private static final Pattern SOURCE_FIXTURE_PATTERN = Pattern.compile(
            "(datacite|crossref)-.*\\.json|medra-.*\\.xml");

    /**
     * Fixtures deliberately not paired with a golden output, with why - e.g. a raw list/search
     * endpoint lookup response consumed as a helper by another fixture's golden test rather than
     * being mapped to a top-level product/grant itself. Add to this only alongside a comment
     * justifying the exclusion.
     */
    private static final Set<String> EXCLUDED_FROM_GOLDEN = Set.of(
            "crossref-journal-doi-lookup-nature.json",
            // Deliberately pins a known limitation (3-letter ISO 639-2 lang codes like "eng"
            // passed through unnormalized - see SKG_IF_DOI_MAPPING_LIMITATIONS.md) via a plain
            // DataCiteToSkgIfMapperTest assertion instead: a golden JSON-LD file would bake a
            // schema-violating "eng" titles key into a committed "expected" fixture, which risks
            // being misread later as sanctioned output rather than a flagged gap.
            "datacite-mixed-lang-titles-eng-fr.json");

    private static String expectedGoldenName(String fixtureName) {
        int dot = fixtureName.lastIndexOf('.');
        return fixtureName.substring(0, dot) + "-out.json";
    }

    @Test
    void everySourceFixtureHasAMatchingGoldenOutput() throws IOException {
        List<String> missingGolden = new ArrayList<>();
        try (var files = Files.list(FIXTURES_DIR)) {
            for (Path file : files.filter(p -> SOURCE_FIXTURE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .toList()) {
                String name = file.getFileName().toString();
                if (EXCLUDED_FROM_GOLDEN.contains(name)) {
                    continue;
                }
                if (!Files.exists(EXPECTED_DIR.resolve(expectedGoldenName(name)))) {
                    missingGolden.add(name);
                }
            }
        }
        assertThat(missingGolden)
                .withFailMessage(
                        "These fixtures under src/test/resources have no matching " +
                                "expected/<name>-out.json: " + missingGolden + ". Add a golden test in " +
                                "ProductsGoldenTest/GrantsGoldenTest and regenerate it (see their class Javadoc), " +
                                "or add the fixture to GoldenFixtureConsistencyTest.EXCLUDED_FROM_GOLDEN with a " +
                                "reason if it's genuinely not a standalone product/grant record (e.g. a list/" +
                                "search-endpoint lookup helper).")
                .isEmpty();
    }
}
