package org.skgif.doi.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Shared ArchUnit setup for every rule class in this package: imports {@code org.skgif.doi..}
 * once, excluding test classes and the {@code org.skgif.doi.generated..} package (build-time
 * openapi-generator output, not hand-written - not a fair target for hand-written-code
 * conventions, and the matching Jacoco exclusion in {@code pom.xml} treats it the same way).
 *
 * <p>Each rule class calls {@link #importedClasses()} once (typically from a {@code @BeforeAll}
 * method) rather than per test method - importing is the slow part of an ArchUnit test.
 */
final class ArchUnitTestSupport {

    private ArchUnitTestSupport() {
    }

    /**
     * Imports the whole {@code org.skgif.doi} main-source tree, excluding test classes and
     * generated code.
     *
     * @return the imported classes, ready for ArchUnit rules to check
     */
    static JavaClasses importedClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/generated/"))
                .importPackages("org.skgif.doi");
    }
}
