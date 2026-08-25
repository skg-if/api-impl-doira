package org.skgif.doi.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.skgif.doi.util.SpotBugsSuppressions.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards against the {@code SKG_IF_DOI_MAPPING*.md} docs drifting out of sync with the
 * DataCite/Crossref test fixtures they document. This can't catch every kind of staleness (a
 * claim in the doc can still go wrong without any file being added, removed, or renamed - see the
 * doc updates that followed adding {@code crossref-journal-article-with-orcid.json} et al., which
 * fixed several claims a mechanical check like this one would have missed), but it catches the two
 * failure modes a file diff can: a new input fixture added without a doc mention, and a doc link
 * pointing at a file that no longer exists.
 *
 * <p>The mapping doc is split across multiple top-level files (an index plus one per
 * entity/topic - see {@code SKG_IF_DOI_MAPPING.md}'s own links) so no single file grows
 * unreadably large. Every file matching {@link #MAPPING_DOC_PATTERN} is treated as part of one
 * combined document for both checks below.
 */
final class MappingDocConsistencyTest {

    /** The repository's root directory. */
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath();
    /** Directory holding the source fixtures the mapping doc is checked against. */
    private static final Path FIXTURES_DIR = REPO_ROOT.resolve("src/test/resources");
    /** Matches one of the split {@code SKG_IF_DOI_MAPPING*.md} doc files. */
    private static final Pattern MAPPING_DOC_PATTERN = Pattern.compile("SKG_IF_DOI_MAPPING.*\\.md");

    /**
     * Fixtures deliberately not covered by the mapping doc, with why - e.g. a golden for a
     * multi-record search/list endpoint rather than the single-record field mapping the doc
     * describes. Add to this only alongside a comment justifying the exclusion.
     */
    private static final Set<String> EXCLUDED_FROM_DOC = Set.of();

    private static List<Path> mappingDocFiles() throws IOException {
        try (var files = Files.list(REPO_ROOT)) {
            return files.filter(MappingDocConsistencyTest::isMappingDoc).toList();
        }
    }

    /**
     * Tests a listed path against {@link #MAPPING_DOC_PATTERN}, as a named method rather than an
     * inline lambda so the {@code getFileName() != null} guard is visible to both nullness
     * analysis and SpotBugs (neither can see into a synthetic lambda's body from the enclosing
     * method's annotations).
     *
     * @param path a path yielded by {@code Files.list(REPO_ROOT)}
     * @return true if the path names one of the split mapping-doc files
     */
    private static boolean isMappingDoc(Path path) {
        Path name = path.getFileName();
        return name != null && MAPPING_DOC_PATTERN.matcher(name.toString()).matches();
    }

    // The bug site below (file.getFileName().toString() at line ~82) is in this method's own
    // body, not a nested lambda, so the enclosing-method @SuppressFBWarnings reliably matches it.
    @SuppressFBWarnings(value = NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE, justification = "Path.getFileName() on " +
            "paths sourced from Files.list over a known directory, never a root path, so it can never actually " +
            "return null even though the JDK contract allows it - " + SPOTBUGS_REGISTER)
    @Test
    void everyTopLevelFixtureIsReferencedInTheMappingDoc() throws IOException {
        StringBuilder allDocs = new StringBuilder();
        for (Path file : mappingDocFiles()) {
            allDocs.append(Files.readString(file));
        }
        String doc = allDocs.toString();

        List<String> undocumented = new ArrayList<>();
        try (var files = Files.list(FIXTURES_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                if (!EXCLUDED_FROM_DOC.contains(name) && !doc.contains(name)) {
                    undocumented.add(name);
                }
            }
        }
        assertThat(undocumented)
                .withFailMessage(
                        "These fixtures under src/test/resources aren't mentioned in any " +
                                "SKG_IF_DOI_MAPPING*.md file: %s. Add a row/link describing what they exercise, " +
                                "or add them to MappingDocConsistencyTest.EXCLUDED_FROM_DOC with a reason if " +
                                "they're genuinely out of scope (e.g. a list/search-endpoint golden).",
                        undocumented)
                .isEmpty();
    }

    @Test
    void everyFixtureLinkInTheMappingDocPointsToARealFile() throws IOException {
        Pattern fixtureLink = Pattern.compile("src/test/resources/[A-Za-z0-9_\\-./]+\\.json");
        List<String> broken = new ArrayList<>();
        for (Path file : mappingDocFiles()) {
            Matcher matcher = fixtureLink.matcher(Files.readString(file));
            while (matcher.find()) {
                String relativePath = matcher.group();
                if (!Files.exists(REPO_ROOT.resolve(relativePath))) {
                    broken.add(file.getFileName() + " -> " + relativePath);
                }
            }
        }
        assertThat(broken)
                .withFailMessage("SKG_IF_DOI_MAPPING*.md links to files that no longer exist: %s", broken)
                .isEmpty();
    }
}
