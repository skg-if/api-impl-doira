package org.skgif.doi.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the stateless-utility-class shape used throughout {@code org.skgif.doi.util}: a
 * {@code final} class with a {@code private} no-arg constructor. The sole exception is
 * {@code LocalIdentifiers}, a {@code @ApplicationScoped} CDI bean that needs
 * {@code @ConfigProperty} injection - CDI requires a non-final, proxyable class, so both rules
 * below exempt any class carrying that annotation. If a second exception type is ever needed,
 * extend the {@code .areNotAnnotatedWith(...)} clause in both rules rather than weakening them.
 */
class UtilityClassShapeArchTest {

    /** The whole {@code org.skgif.doi} main-source tree, imported once for every rule below. */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchUnitTestSupport.importedClasses();
    }

    /**
     * Excludes interfaces: {@code package-info.java} compiles to a synthetic {@code package-info}
     * interface, which also resides in {@code org.skgif.doi.util} but is never final and isn't a
     * utility class this rule is about.
     */
    @Test
    void utilityClassesAreFinal() {
        classes().that().resideInAPackage("org.skgif.doi.util")
                .and().areNotAnnotatedWith(ApplicationScoped.class)
                .and().areNotInterfaces()
                .should().haveModifier(JavaModifier.FINAL)
                .check(classes);
    }

    @Test
    void utilityClassConstructorsArePrivate() {
        constructors().that().areDeclaredInClassesThat().resideInAPackage("org.skgif.doi.util")
                .and().areDeclaredInClassesThat().areNotAnnotatedWith(ApplicationScoped.class)
                .should().bePrivate()
                .check(classes);
    }
}
