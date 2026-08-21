package org.skgif.doi.rest;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.skgif.doi.spec.GrantFilterKeys;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Translates the SKG-IF {@code filter} query syntax into a DataCite REST API {@code query}
 * string for the {@code /datacite/grants} endpoint - see {@link FilterQuerySyntax} for the shared
 * comma-splitting mechanics, and {@link DataCiteProductFilters} for the equivalent on {@code
 * /datacite/products}. Only a modest, explicitly-supported subset of the spec's Grant filter keys is
 * implemented - per the spec, "each filter implementation is optional" and unsupported filters
 * must 422.
 *
 * <p>Note the spec's own Grant filter table uses {@code identifiers.value} / {@code
 * contributions.by.identifiers.value} / etc. (the {@code .value} suffix), whereas its Product
 * filter table uses {@code identifiers.id} for the same concept - an inconsistency in the spec
 * itself, not something introduced here; each entity's filters follow its own documented table.
 *
 * <p>Deliberately NOT implemented, since {@code DataCiteToSkgIfMapper.toGrant} never populates
 * these fields (no reliable generic DataCite source for them - see its class javadoc):
 * {@code acronym}, {@code currency}, {@code website}, {@code funding_stream}, {@code
 * grant_number}, {@code contributions.role}, and the {@code short_name}/{@code website}/{@code
 * country} sub-filters of {@code beneficiaries.*}, {@code contributions.declared_affiliations.*}
 * and {@code funding_agency.*}.
 */
final class DataCiteGrantFilters {

    /** DataCite query clause guaranteed to match no record, for a filter that can't be satisfied. */
    private static final String NO_MATCH_CLAUSE = FilterQuerySyntax.NO_MATCH_CLAUSE;
    /** SKG-IF identifier scheme name for a ROR id. */
    private static final String SCHEME_ROR = IdentifierScheme.ROR.value();

    // Every GrantFilterKeys constant is implemented below, so supportedKeys is derived directly
    // from the enum rather than re-listed - a newly-added constant can't silently fall out of sync.
    /** The {@link GrantFilterKeys} values this endpoint supports. */
    private static final Set<String> SUPPORTED = Arrays.stream(GrantFilterKeys.values())
            .map(GrantFilterKeys::key)
            .collect(Collectors.toUnmodifiableSet());

    private DataCiteGrantFilters() {
    }

    static Optional<String> toDataCiteQuery(String filter) {
        if (filter == null || filter.isBlank()) {
            return Optional.empty();
        }
        List<String> clauses = FilterQuerySyntax.parseClauses(filter, SUPPORTED, DataCiteGrantFilters::toClause);
        return clauses.isEmpty() ? Optional.empty() : Optional.of(String.join(" AND ", clauses));
    }

    /**
     * Maps every {@link GrantFilterKeys} constant to its clause builder - see {@link #toClause}.
     * Package-private (not private) so {@code DataCiteGrantFiltersTest} can assert it covers every
     * enum constant.
     */
    static final Map<GrantFilterKeys, Function<String, String>> CLAUSE_BUILDERS = new EnumMap<>(GrantFilterKeys.class);

    static {
        CLAUSE_BUILDERS.put(GrantFilterKeys.IDENTIFIERS_VALUE,
                value -> IdentifierScheme.DOI.value() + ":\"" + escape(value) + "\"");
        // We only ever expose doi identifiers, so any other requested scheme never matches.
        CLAUSE_BUILDERS.put(GrantFilterKeys.IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, IdentifierScheme.DOI.value(), NO_MATCH_CLAUSE));

        // contributions.by.* - populated from remaining creators[] + all contributors[]
        // (see DataCiteToSkgIfMapper.toGrant), so every by-filter matches against either.
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER,
                value -> FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier", value));
        // Unlike Product contributions (always a person -> orcid only), Grant contributions
        // can be organisational (-> ror), so both schemes are valid here.
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
                value -> (IdentifierScheme.ORCID.value().equalsIgnoreCase(value) || SCHEME_ROR.equalsIgnoreCase(value)) ?
                        null : NO_MATCH_CLAUSE);
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE,
                DataCiteGrantFilters::byIdentifierValueClause);
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("familyName", value));
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("givenName", value));
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("name", value));

        // contributions.declared_affiliations.* - built from creator/contributor
        // affiliation[], same as Product.
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER,
                value -> FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", value));
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_VALUE,
                value -> FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier",
                        ExternalIdentifierUrls.ROR_BASE_URL + value));
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE));
        CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("affiliation.name", value));

        // beneficiaries.* - organisational contributors only (see
        // DataCiteToSkgIfMapper.grantBeneficiaries), but DataCite has no way to constrain a
        // flat query to just the organisational subset, so this matches all contributors.
        CLAUSE_BUILDERS.put(GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE));
        CLAUSE_BUILDERS.put(GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_VALUE,
                value -> rorClause("contributors.nameIdentifiers.nameIdentifier", value));
        CLAUSE_BUILDERS.put(GrantFilterKeys.BENEFICIARIES_NAME, value -> "contributors.name:\"" + escape(value) + "\"");

        // funding_agency.* - the first ROR-bearing creator (falling back to publisher, which
        // has no separate DataCite field to filter on beyond the creator name/ROR itself).
        CLAUSE_BUILDERS.put(GrantFilterKeys.FUNDING_AGENCY_NAME, value -> "creators.name:\"" + escape(value) + "\"");
        CLAUSE_BUILDERS.put(GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE));
        CLAUSE_BUILDERS.put(GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE,
                value -> rorClause("creators.nameIdentifiers.nameIdentifier", value));

        UnaryOperator<String> searchClauseBuilder = DataCiteGrantFilters::escape;
        CLAUSE_BUILDERS.put(GrantFilterKeys.CF_SEARCH_TITLE, searchClauseBuilder);
        CLAUSE_BUILDERS.put(GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT, searchClauseBuilder);
    }

    // Sole call site is toDataCiteQuery's `FilterQuerySyntax.parseClauses(filter, SUPPORTED,
    // DataCiteGrantFilters::toClause)` above - PMD's symbol table doesn't reliably trace a private
    // method through a method reference passed as the BinaryOperator<String> clause-builder
    // argument once the generated OpenAPI sources are on the compile classpath, so it misreports
    // this method as unused.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static String toClause(String key, String value) {
        return CLAUSE_BUILDERS.get(GrantFilterKeys.fromKey(key)).apply(value);
    }

    /**
     * A {@code contributions.by.identifiers.value} filter doesn't say which scheme it's for
     * (unlike Product, where it's always orcid) - so this matches either an ORCID or a ROR full
     * URL against either creators or contributors.
     *
     * @param bareValue the bare ORCID or ROR id from the filter value
     * @return a Lucene clause matching bareValue as either an ORCID or ROR URL, on either role
     */
    private static String byIdentifierValueClause(String bareValue) {
        String orcid = ExternalIdentifierUrls.ORCID_BASE_URL + escape(bareValue);
        String ror = ExternalIdentifierUrls.ROR_BASE_URL + escape(bareValue);
        return "(creators.nameIdentifiers.nameIdentifier:\"" + orcid +
                "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcid +
                "\" OR creators.nameIdentifiers.nameIdentifier:\"" + ror +
                "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + ror + "\")";
    }

    /**
     * Bare ROR id -> the given single field, matched against the full ROR URL.
     *
     * @param field   the DataCite field to match against
     * @param bareRor the bare ROR id from the filter value
     * @return a Lucene clause matching field against the full ROR URL for bareRor
     */
    private static String rorClause(String field, String bareRor) {
        return field + ":\"" + escape(ExternalIdentifierUrls.ROR_BASE_URL + bareRor) + "\"";
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
