package org.skgif.doi.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the repo's two toolchain version pins against drifting back into copies.
 *
 * <p>{@code pom.xml}'s {@code <maven.compiler.release>} is the single source of truth for the Java
 * version, and {@code .mvn/wrapper/maven-wrapper.properties} is the single source of truth for
 * Maven. Most consumers derive from those: the {@code skg-if-build-toolchain} activation scripts
 * read the pom, CI reads it through {@code .github/actions/setup-java-from-pom}, and every build
 * goes through the {@code ./mvnw} wrapper.
 *
 * <p>Three consumers cannot derive anything, because a Docker image tag, a devcontainer image tag,
 * and a line of README prose are all resolved long before any script could run. This test is what
 * keeps those honest, plus a check that the agent skills never reacquire the hardcoded
 * {@code jdk-21}/{@code apache-maven-3.9.16} paths they used to carry.
 *
 * <p>What it can't catch: a pin that is syntactically consistent but wrong everywhere at once (all
 * files agreeing on a Java version the code doesn't actually build under), or a skill that
 * describes the toolchain incorrectly in prose without naming a version.
 */
class ToolchainVersionConsistencyTest {

    /** The repository's root directory. */
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath();

    /** Extracts the Java feature version from the pom property that owns it. */
    private static final Pattern RELEASE_PROPERTY = Pattern.compile("<maven\\.compiler\\.release>\\s*(\\d+)\\s*<");

    /** Toolchain paths that used to be pasted into every skill; none may come back. */
    private static final Pattern VERSION_LITERAL = Pattern.compile("jdk-\\d+|apache-maven-\\d+\\.\\d+");

    /**
     * The only file legitimately allowed to contain a versioned toolchain path: ISSUES.md is a
     * historical log that quotes the old commands on purpose. The activation and bootstrap scripts
     * are deliberately not exempt - they build {@code jdk-<major>} from the pom-derived version, so
     * a literal appearing in one of them is exactly the regression this guards against.
     */
    private static final Set<String> LITERAL_ALLOWED = Set.of("ISSUES.md");

    /** Directory holding the activation and bootstrap scripts. */
    private static final Path TOOLCHAIN_SKILL = Path.of(".claude/skills/skg-if-build-toolchain");

    private static String javaFeatureVersion() throws IOException {
        Matcher matcher = RELEASE_PROPERTY.matcher(Files.readString(REPO_ROOT.resolve("pom.xml")));
        assertThat(matcher.find())
                .withFailMessage("pom.xml no longer declares <maven.compiler.release>. It is the single source of " +
                        "truth for this repo's Java version - the activation scripts and " +
                        ".github/actions/setup-java-from-pom both parse it, so it must stay a bare major version.")
                .isTrue();
        return matcher.group(1);
    }

    @Test
    void javaVersionIsConsistentAcrossThePinsThatCannotDeriveIt() throws IOException {
        String version = javaFeatureVersion();

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("src/main/docker/Dockerfile.jvm", "openjdk-" + version + ":");
        expected.put("README.md", "JDK " + version + "+");

        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Files.readString(REPO_ROOT.resolve(entry.getKey())).contains(entry.getValue())) {
                stale.add(entry.getKey() + " (expected to contain \"" + entry.getValue() + "\")");
            }
        }

        // The devcontainer tag carries two independent versions - Microsoft's own image major and
        // the Java major - and they do not advance together: the "1-<java>" line stopped at Java 21,
        // so Java 25 ships as "3-25-bookworm". Only the Java half is ours to keep in step, so match
        // the image major loosely rather than pinning a prefix that breaks at the next bump.
        Pattern devcontainerTag = Pattern.compile("java:\\d+(?:\\.\\d+)*-" + version + "-bookworm");
        String devcontainer = Files.readString(REPO_ROOT.resolve(".devcontainer/devcontainer.json"));
        if (!devcontainerTag.matcher(devcontainer).find()) {
            stale.add(".devcontainer/devcontainer.json (expected to match \"" + devcontainerTag.pattern() + "\")");
        }

        assertThat(stale)
                .withFailMessage("pom.xml declares Java " + version + ", but these files still pin a different " +
                        "version: " + stale + ". They can't read pom.xml at the point they're evaluated (two image " +
                        "tags and README prose), so update each by hand to match.")
                .isEmpty();
    }

    @Test
    void skillsAndLaunchConfigCarryNoToolchainVersionLiterals() throws IOException {
        List<Path> scanned = new ArrayList<>();
        Path skills = REPO_ROOT.resolve(".claude/skills");
        try (Stream<Path> tree = Files.walk(skills)) {
            tree.filter(Files::isRegularFile)
                    .filter(ToolchainVersionConsistencyTest::isScannableSkillFile)
                    .forEach(scanned::add);
        }
        scanned.add(REPO_ROOT.resolve(".claude/launch.json"));

        List<String> offenders = new ArrayList<>();
        for (Path file : scanned) {
            if (LITERAL_ALLOWED.contains(file.getFileName().toString())) {
                continue;
            }
            Matcher matcher = VERSION_LITERAL.matcher(Files.readString(file));
            if (matcher.find()) {
                offenders.add(REPO_ROOT.relativize(file) + " -> " + matcher.group());
            }
        }

        assertThat(offenders)
                .withFailMessage("These files hardcode a toolchain version that used to be duplicated across every " +
                        "skill: " + offenders + ". Dot-source " +
                        ".claude/skills/skg-if-build-toolchain/activate.ps1 (or source activate.sh) and call " +
                        "./mvnw instead - the JDK version comes from pom.xml and Maven's from the wrapper.")
                .isEmpty();
    }

    /**
     * Adoptium serves a {@code .zip} for Windows and a {@code .tar.gz} everywhere else, so the two
     * bootstrap scripts cannot share an extractor. Everything <em>around</em> the extractor is
     * supposed to be identical, and this pins that down: if a later edit changes the download
     * endpoint or the cache layout in one script, the other must move with it or fail here.
     *
     * @throws IOException if either bootstrap script cannot be read
     */
    @Test
    void bothBootstrapScriptsAgreeOnEverythingButTheExtractor() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        for (String name : List.of("bootstrap-jdk.ps1", "bootstrap-jdk.sh")) {
            scripts.put(name, Files.readString(REPO_ROOT.resolve(TOOLCHAIN_SKILL).resolve(name)));
        }

        Map<String, Pattern> conventions = new LinkedHashMap<>();
        conventions.put("the Adoptium latest-GA endpoint (https://api.adoptium.net/v3/binary/latest/)",
                Pattern.compile(Pattern.quote("https://api.adoptium.net/v3/binary/latest/")));
        conventions.put("the Adoptium binary selector (/jdk/hotspot/normal/eclipse)",
                Pattern.compile(Pattern.quote("/jdk/hotspot/normal/eclipse")));
        conventions.put("the .tools cache directory", Pattern.compile("\\.tools"));
        conventions.put("a jdk-<derived major> directory name", Pattern.compile("jdk-\\$\\{?\\w+"));

        List<String> drifted = new ArrayList<>();
        for (Map.Entry<String, String> script : scripts.entrySet()) {
            for (Map.Entry<String, Pattern> convention : conventions.entrySet()) {
                if (!convention.getValue().matcher(script.getValue()).find()) {
                    drifted.add(script.getKey() + " no longer references " + convention.getKey());
                }
            }
        }

        assertThat(drifted)
                .withFailMessage("The Windows and Linux JDK bootstrap scripts have drifted apart: " + drifted +
                        ". They may differ only in how they unpack the archive (Expand-Archive vs tar) - the " +
                        "download URL and the .tools/jdk-<major> cache layout must stay identical, because " +
                        "activate.sh dispatches to either one and then checks the same path afterwards.")
                .isEmpty();
    }

    private static boolean isScannableSkillFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".md") || name.endsWith(".ps1") || name.endsWith(".sh") || name.endsWith(".json");
    }
}
