package org.skgif.doi.util;

import java.util.Optional;

/**
 * Every SpotBugs/FindSecBugs bug pattern this codebase suppresses at one or more sites, paired with
 * the category the reporting tool files it under and a link to its upstream description.
 *
 * <p>Suppressions live as per-site {@code @SuppressFBWarnings} annotations rather than
 * {@code spotbugs-exclude.xml} entries (see {@code STATIC_ANALYSIS_POLICY.md}'s SpotBugs decision
 * register for why), and an annotation argument must be a compile-time constant - which an enum
 * constant is not. The pattern names therefore also exist as {@code String} constants in the nested
 * {@link Code} holder, and that is what annotated sites {@code import static}. This enum is the
 * descriptive layer over those constants: it carries the reporting category and the documentation
 * link a bare {@code String} cannot, and {@code SpotBugsErrorTest} fails the build if a constant in
 * {@link Code} ever loses its matching enum member or vice versa.
 *
 * <p>Naming the patterns in one place at all is deliberate: a typo in a repeated string literal
 * silently no-ops the suppression rather than failing anything, and PMD's
 * {@code AvoidDuplicateLiterals} otherwise fires on a class annotating several methods with the
 * same pattern.
 */
public enum SpotBugsError {

    // Malicious code vulnerability
    /**
     * May expose internal representation by returning a reference to a mutable object - reported on
     * a getter.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#ei-may-expose-internal-representation-by-returning-reference-to-mutable-object-ei-expose-rep">EI_EXPOSE_REP</a>
     */
    EI_EXPOSE_REP(Code.EI_EXPOSE_REP, Category.MALICIOUS_CODE),
    /**
     * May expose internal representation by incorporating a reference to a mutable object - reported
     * on a constructor or setter.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#ei2-may-expose-internal-representation-by-incorporating-reference-to-mutable-object-ei-expose-rep2">EI_EXPOSE_REP2</a>
     */
    EI_EXPOSE_REP2(Code.EI_EXPOSE_REP2, Category.MALICIOUS_CODE),

    // Correctness
    /**
     * An {@code instanceof} bytecode analysis considers redundant.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#sio-unnecessary-type-check-done-using-instanceof-operator-sio-superfluous-instanceof">SIO_SUPERFLUOUS_INSTANCEOF</a>
     */
    SIO_SUPERFLUOUS_INSTANCEOF(Code.SIO_SUPERFLUOUS_INSTANCEOF, Category.CORRECTNESS),

    // Dodgy code
    /**
     * An {@code instanceof} that bytecode analysis can prove always true.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#bc-instanceof-will-always-return-true-bc-vacuous-instanceof">BC_VACUOUS_INSTANCEOF</a>
     */
    BC_VACUOUS_INSTANCEOF(Code.BC_VACUOUS_INSTANCEOF, Category.STYLE),
    /**
     * A return value used on some path without a null check - filed under dodgy code rather than
     * correctness upstream, and superseded here by NullAway's source-level tracking.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#np-possible-null-pointer-dereference-due-to-return-value-of-called-method-np-null-on-some-path-from-return-value">NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE</a>
     */
    NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE(Code.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE, Category.STYLE),
    /**
     * A local variable assigned but never read.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#dls-dead-store-to-local-variable-dls-dead-local-store">DLS_DEAD_LOCAL_STORE</a>
     */
    DLS_DEAD_LOCAL_STORE(Code.DLS_DEAD_LOCAL_STORE, Category.STYLE),
    /**
     * A broad {@code catch (Exception ...)}.
     *
     * @see <a
     *      href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#rec-exception-is-caught-when-exception-is-not-thrown-rec-catch-exception">REC_CATCH_EXCEPTION</a>
     */
    REC_CATCH_EXCEPTION(Code.REC_CATCH_EXCEPTION, Category.STYLE),

    // Security - all four are FindSecBugs patterns, not vanilla SpotBugs ones
    /**
     * FindSecBugs's taint-source marker on every JAX-RS endpoint method.
     *
     * @see <a href="https://find-sec-bugs.github.io/bugs.htm#JAXRS_ENDPOINT">JAXRS_ENDPOINT</a>
     */
    JAXRS_ENDPOINT(Code.JAXRS_ENDPOINT, Category.SECURITY),
    /**
     * FindSecBugs's locale-sensitive case-mapping/comparison marker.
     *
     * @see <a href="https://find-sec-bugs.github.io/bugs.htm#IMPROPER_UNICODE">IMPROPER_UNICODE</a>
     */
    IMPROPER_UNICODE(Code.IMPROPER_UNICODE, Category.SECURITY),
    /**
     * FindSecBugs's XPath-injection marker.
     *
     * @see <a href="https://find-sec-bugs.github.io/bugs.htm#XPATH_INJECTION">XPATH_INJECTION</a>
     */
    XPATH_INJECTION(Code.XPATH_INJECTION, Category.SECURITY),
    /**
     * FindSecBugs's path-traversal marker on a {@code File}/{@code Path} constructed from a String.
     *
     * @see <a href="https://find-sec-bugs.github.io/bugs.htm#PATH_TRAVERSAL_IN">PATH_TRAVERSAL_IN</a>
     */
    PATH_TRAVERSAL_IN(Code.PATH_TRAVERSAL_IN, Category.SECURITY);

    /**
     * Justification suffix shared by every suppression, pointing at the durable record of why each
     * one is safe.
     */
    public static final String SPOTBUGS_REGISTER = "see STATIC_ANALYSIS_POLICY.md's SpotBugs decision register";

    // Fields intentionally share their names with their accessors below, same idiom
    // EntityTypes/GrantFilterKeys/ProductFilterKeys already use.
    /** The constant's underlying SpotBugs pattern name, as it appears in a suppression. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String code;

    /** The category the reporting tool files this pattern under. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final Category category;

    SpotBugsError(String code, Category category) {
        this.code = code;
        this.category = category;
    }

    /**
     * Unwraps the constant to the raw pattern name a {@code @SuppressFBWarnings} value carries.
     *
     * @return the SpotBugs/FindSecBugs pattern name this constant represents
     */
    public String code() {
        return code;
    }

    /**
     * Reports which of the reporting tool's categories this pattern belongs to.
     *
     * @return the category SpotBugs or FindSecBugs files this pattern under
     */
    public Category category() {
        return category;
    }

    /**
     * Resolves a raw pattern name - typically one read back out of a SpotBugs report - to its
     * constant.
     *
     * <p>Matching is exact rather than case-insensitive: pattern names are a fixed upper-case
     * vocabulary, and a case-insensitive comparison here would itself be a FindSecBugs
     * {@link #IMPROPER_UNICODE} site.
     *
     * @param code a raw pattern name, expected to match one of this enum's {@link #code()} values
     * @return the matching constant, or {@link Optional#empty()} for a pattern this codebase does
     *         not suppress anywhere
     */
    public static Optional<SpotBugsError> fromCode(String code) {
        for (SpotBugsError candidate : values()) {
            if (candidate.code.equals(code)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The reporting categories SpotBugs groups its bug patterns into, spelled as a
     * {@code BugPattern} declaration spells them - FindSecBugs registers all of its own patterns
     * under {@link #SECURITY}.
     *
     * <p>Listed in full rather than only the values used above, so a pattern added to
     * {@link SpotBugsError} later has a category to name without this enum needing an edit too.
     *
     * @see <a href="https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html">SpotBugs bug descriptions</a>
     */
    public enum Category {

        BAD_PRACTICE,
        CORRECTNESS,
        EXPERIMENTAL,
        I18N,
        MT_CORRECTNESS,
        NOISE,
        MALICIOUS_CODE,
        PERFORMANCE,
        SECURITY,
        STYLE
    }

    /**
     * The pattern names again as compile-time {@code String} constants, which is the only form a
     * {@code @SuppressFBWarnings} argument can take.
     *
     * <p>Each constant is the sole argument of the matching {@link SpotBugsError} member above, so
     * the two cannot disagree on the spelling of a name; what a test still has to check is that
     * neither side gained or lost an entry.
     */
    // A constants-only holder is the intended shape here, not an anemic domain object PMD's
    // DataClass rule is meant to flag - unlike EntityTypes/GrantFilterKeys/ProductFilterKeys
    // (pmd-ruleset.xml's DataClass note), this can't be converted to an enum instead: a
    // @SuppressFBWarnings argument must be a compile-time constant, which an enum constant is not.
    @SuppressWarnings("PMD.DataClass")
    public static final class Code {

        /** Pattern name for {@link SpotBugsError#EI_EXPOSE_REP}. */
        public static final String EI_EXPOSE_REP = "EI_EXPOSE_REP";
        /** Pattern name for {@link SpotBugsError#EI_EXPOSE_REP2}. */
        public static final String EI_EXPOSE_REP2 = "EI_EXPOSE_REP2";
        /** Pattern name for {@link SpotBugsError#SIO_SUPERFLUOUS_INSTANCEOF}. */
        public static final String SIO_SUPERFLUOUS_INSTANCEOF = "SIO_SUPERFLUOUS_INSTANCEOF";
        /** Pattern name for {@link SpotBugsError#BC_VACUOUS_INSTANCEOF}. */
        public static final String BC_VACUOUS_INSTANCEOF = "BC_VACUOUS_INSTANCEOF";
        /** Pattern name for {@link SpotBugsError#NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE}. */
        public static final String NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE";
        /** Pattern name for {@link SpotBugsError#DLS_DEAD_LOCAL_STORE}. */
        public static final String DLS_DEAD_LOCAL_STORE = "DLS_DEAD_LOCAL_STORE";
        /** Pattern name for {@link SpotBugsError#REC_CATCH_EXCEPTION}. */
        public static final String REC_CATCH_EXCEPTION = "REC_CATCH_EXCEPTION";
        /** Pattern name for {@link SpotBugsError#JAXRS_ENDPOINT}. */
        public static final String JAXRS_ENDPOINT = "JAXRS_ENDPOINT";
        /** Pattern name for {@link SpotBugsError#IMPROPER_UNICODE}. */
        public static final String IMPROPER_UNICODE = "IMPROPER_UNICODE";
        /** Pattern name for {@link SpotBugsError#XPATH_INJECTION}. */
        public static final String XPATH_INJECTION = "XPATH_INJECTION";
        /** Pattern name for {@link SpotBugsError#PATH_TRAVERSAL_IN}. */
        public static final String PATH_TRAVERSAL_IN = "PATH_TRAVERSAL_IN";

        private Code() {
        }
    }
}
