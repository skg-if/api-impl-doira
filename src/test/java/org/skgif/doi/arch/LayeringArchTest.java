package org.skgif.doi.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards two layering invariants that hold today (grep-verified) between {@code org.skgif.doi.rest}
 * and the rest of the codebase.
 */
final class LayeringArchTest {

    /** The whole {@code org.skgif.doi} main-source tree, imported once for every rule below. */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchUnitTestSupport.importedClasses();
    }

    /**
     * {@code rest} is the top layer: it depends on the providers and on {@code util}, but nothing
     * outside {@code rest} depends back on it (grep-verified zero hits for
     * {@code import org.skgif.doi.rest.} anywhere else in the tree before this rule was written).
     */
    @Test
    void nothingOutsideRestDependsOnRest() {
        noClasses().that().resideOutsideOfPackage("org.skgif.doi.rest..")
                .should().dependOnClassesThat().resideInAPackage("org.skgif.doi.rest..")
                .check(classes);
    }

    /**
     * The shared {@code mapper}/{@code spec}/{@code util} packages never reach into a provider's
     * internal {@code dto}/{@code xml} packages (grep-verified zero hits today). This is
     * deliberately narrower than "nothing outside a provider touches that provider's dto/xml" -
     * {@code org.skgif.doi.rest} legitimately reaches into provider {@code dto}/{@code xml}
     * internals (e.g. {@code CrossrefVenueEnricher} uses {@code crossref.xml},
     * {@code MedraProductsResource} uses {@code medra.xml}), so a broader rule would fail against
     * real, intentional code. Cross-provider {@code dto}/{@code xml} leakage (e.g. {@code crossref}
     * reaching into {@code datacite.dto}) is already covered by
     * {@link ProviderIndependenceArchTest}'s provider-independence rules.
     */
    @Test
    void sharedPackagesDoNotDependOnProviderInternals() {
        noClasses().that().resideInAnyPackage("org.skgif.doi.mapper..", "org.skgif.doi.spec..", "org.skgif.doi.util..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.skgif.doi.crossref.dto..", "org.skgif.doi.crossref.xml..",
                        "org.skgif.doi.datacite.dto..",
                        "org.skgif.doi.medra.dto..", "org.skgif.doi.medra.xml..")
                .check(classes);
    }
}
