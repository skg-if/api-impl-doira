package org.skgif.doi.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Freezes a coding choice that holds throughout {@code org.skgif.doi} today: no logging
 * framework and no direct standard-stream access, exceptions propagate via checked throws
 * instead. This documents the current convention so a future change to it is a deliberate,
 * reviewed decision rather than an accidental one-off {@code System.out.println} or a new
 * logging dependency creeping in - it is not meant as a permanent architectural law.
 */
final class CodingConventionArchTest {

    /** The whole {@code org.skgif.doi} main-source tree, imported once for every rule below. */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchUnitTestSupport.importedClasses();
    }

    @Test
    void noClassAccessesStandardStreams() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
    }

    @Test
    void noClassDependsOnALoggingFramework() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "org.slf4j..", "org.jboss.logging..", "java.util.logging..")
                .check(classes);
    }
}
