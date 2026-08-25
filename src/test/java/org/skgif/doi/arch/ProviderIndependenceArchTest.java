package org.skgif.doi.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the three DOI-registration-agency providers - {@code crossref}, {@code datacite}, and
 * {@code medra} - stay independent siblings: none of them may import another's domain code
 * ({@code org.skgif.doi.<provider>..}) or REST code ({@code org.skgif.doi.rest.<provider>..}).
 * This has always been true in practice (verified by grep before writing these rules); the point
 * of an enforced rule is to keep it true as the codebase grows, rather than relying on review to
 * catch a stray cross-provider import.
 *
 * <p>The {@code rest.<provider>} half of each rule only became checkable once
 * {@code org.skgif.doi.rest} was split into one subpackage per provider - before that split, the
 * REST layer had no package boundary between providers to check against.
 */
final class ProviderIndependenceArchTest {

    /** The whole {@code org.skgif.doi} main-source tree, imported once for every rule below. */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchUnitTestSupport.importedClasses();
    }

    @Test
    void crossrefDoesNotDependOnOtherProviders() {
        noClasses().that().resideInAnyPackage("org.skgif.doi.crossref..", "org.skgif.doi.rest.crossref..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.skgif.doi.datacite..", "org.skgif.doi.rest.datacite..",
                        "org.skgif.doi.medra..", "org.skgif.doi.rest.medra..")
                .check(classes);
    }

    @Test
    void dataciteDoesNotDependOnOtherProviders() {
        noClasses().that().resideInAnyPackage("org.skgif.doi.datacite..", "org.skgif.doi.rest.datacite..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.skgif.doi.crossref..", "org.skgif.doi.rest.crossref..",
                        "org.skgif.doi.medra..", "org.skgif.doi.rest.medra..")
                .check(classes);
    }

    @Test
    void medraDoesNotDependOnOtherProviders() {
        noClasses().that().resideInAnyPackage("org.skgif.doi.medra..", "org.skgif.doi.rest.medra..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.skgif.doi.crossref..", "org.skgif.doi.rest.crossref..",
                        "org.skgif.doi.datacite..", "org.skgif.doi.rest.datacite..")
                .check(classes);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices().matching("org.skgif.doi.(*)..").should().beFreeOfCycles().check(classes);
    }
}
