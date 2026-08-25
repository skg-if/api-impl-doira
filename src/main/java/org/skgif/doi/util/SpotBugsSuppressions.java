package org.skgif.doi.util;

/**
 * SpotBugs/FindSecBugs pattern-name and shared-phrase constants for {@code @SuppressFBWarnings}
 * annotations across the codebase, so a pattern name is spelled once rather than repeated as a
 * string literal at every call site (which both risks a typo silently no-op'ing the suppression
 * and trips PMD's {@code AvoidDuplicateLiterals} within a file annotating several methods with the
 * same pattern). See {@code STATIC_ANALYSIS_POLICY.md}'s SpotBugs decision register for what each
 * pattern covers and why it's suppressed where it's used.
 */
// A constants-only holder is the intended shape here, not an anemic domain object PMD's DataClass
// rule is meant to flag - the alternative (a Java interface of constants) is the worse-regarded
// idiom this final-class-with-private-constructor pattern deliberately replaces.
@SuppressWarnings("PMD.DataClass")
public final class SpotBugsSuppressions {

    private SpotBugsSuppressions() {
    }

    /** May-expose-internal-representation via a getter. */
    public static final String EI_EXPOSE_REP = "EI_EXPOSE_REP";
    /** May-expose-internal-representation via a constructor/setter. */
    public static final String EI_EXPOSE_REP2 = "EI_EXPOSE_REP2";
    /** An {@code instanceof} that bytecode analysis can prove always true. */
    public static final String BC_VACUOUS_INSTANCEOF = "BC_VACUOUS_INSTANCEOF";
    /** An {@code instanceof} bytecode analysis considers redundant. */
    public static final String SIO_SUPERFLUOUS_INSTANCEOF = "SIO_SUPERFLUOUS_INSTANCEOF";
    /** A return value used on some path without a null check. */
    public static final String NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE";
    /** A local variable assigned but never read. */
    public static final String DLS_DEAD_LOCAL_STORE = "DLS_DEAD_LOCAL_STORE";
    /** A broad {@code catch (Exception ...)}. */
    public static final String REC_CATCH_EXCEPTION = "REC_CATCH_EXCEPTION";
    /** FindSecBugs's taint-source marker on every JAX-RS endpoint method. */
    public static final String JAXRS_ENDPOINT = "JAXRS_ENDPOINT";
    /** FindSecBugs's locale-sensitive case-mapping/comparison marker. */
    public static final String IMPROPER_UNICODE = "IMPROPER_UNICODE";
    /** FindSecBugs's XPath-injection marker. */
    public static final String XPATH_INJECTION = "XPATH_INJECTION";
    /** FindSecBugs's path-traversal marker on a {@code File}/{@code Path} constructed from a String. */
    public static final String PATH_TRAVERSAL_IN = "PATH_TRAVERSAL_IN";

    /** Common justification suffix pointing at the durable record of why each suppression is safe. */
    public static final String SPOTBUGS_REGISTER = "see STATIC_ANALYSIS_POLICY.md's SpotBugs decision register";
}
