package org.skgif.doi.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards this codebase's suffix-to-package naming conventions, one direction only: a class with
 * a given suffix must live in the expected package(s), but a class in that package is not
 * required to carry the suffix - several legitimate classes in {@code org.skgif.doi.rest}
 * (e.g. {@code RestApplication}, {@code RootRoutes}, the {@code JsonLd*} helpers) don't.
 */
class NamingConventionArchTest {

    /** The whole {@code org.skgif.doi} main-source tree, imported once for every rule below. */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchUnitTestSupport.importedClasses();
    }

    /**
     * Scoped outside {@code ..dto..}: provider wire-format DTOs may legitimately reuse the
     * "Resource" word for their own domain concept (e.g. {@code CrossrefResource} mirrors
     * Crossref's own {@code resource} JSON field, unrelated to a JAX-RS resource class).
     */
    @Test
    void resourceClassesResideInRest() {
        classes().that().haveSimpleNameEndingWith("Resource")
                .and().resideOutsideOfPackage("..dto..")
                .should().resideInAPackage("org.skgif.doi.rest..")
                .check(classes);
    }

    @Test
    void filtersClassesResideInRest() {
        classes().that().haveSimpleNameEndingWith("Filters")
                .should().resideInAPackage("org.skgif.doi.rest..")
                .check(classes);
    }

    /**
     * Matches both the top-level provider-agnostic interfaces in {@code org.skgif.doi.mapper}
     * (e.g. {@code GrantCapableMapper}) and each provider's own {@code <provider>.mapper}
     * subpackage (e.g. {@code CrossrefToSkgIfMapper}). Scoped outside {@code org.skgif.doi.util}:
     * {@code LicenceMapper} is a general string/URL helper that happens to use the word "Mapper",
     * not a SKG-IF entity-field mapper in the sense this rule is guarding.
     */
    @Test
    void mapperClassesResideInMapperPackages() {
        classes().that().haveSimpleNameEndingWith("Mapper")
                .and().resideOutsideOfPackage("org.skgif.doi.util")
                .should().resideInAPackage("..mapper..")
                .check(classes);
    }

    @Test
    void parserClassesResideInXmlPackages() {
        classes().that().haveSimpleNameEndingWith("Parser")
                .should().resideInAPackage("..xml..")
                .check(classes);
    }

    /**
     * Exact (non-recursive) package match: {@code CrossrefWorkFetcher}/{@code DataCiteDoiFetcher}
     * live directly in their provider's root package, not a subpackage. {@code medra} is included
     * even though it has no {@code Fetcher} class today - this only constrains where a class named
     * {@code *Fetcher} may live, not whether one must exist.
     */
    @Test
    void fetcherClassesResideInProviderRootPackages() {
        classes().that().haveSimpleNameEndingWith("Fetcher")
                .should().resideInAnyPackage(
                        "org.skgif.doi.crossref", "org.skgif.doi.datacite", "org.skgif.doi.medra")
                .check(classes);
    }
}
