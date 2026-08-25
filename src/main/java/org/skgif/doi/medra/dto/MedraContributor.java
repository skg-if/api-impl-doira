package org.skgif.doi.medra.dto;

import org.jspecify.annotations.Nullable;

/**
 * One ONIX-for-DOI {@code <Contributor>} element. {@code role} is the raw {@code ContributorRole}
 * code (e.g. {@code "A01"} for author - the only value observed in practice). The four name
 * fields are mutually exclusive in practice but all nullable here - a record carries exactly one
 * of {@code namesBeforeKey}+{@code keyNames}, {@code personName} (alone or with {@code
 * personNameInverted}), or {@code personNameInverted} alone - see {@code
 * MedraContributionMapper#personRef} for the precedence used to resolve them into a display name.
 *
 * @param role               the raw ContributorRole code (e.g. "A01" for author)
 * @param namesBeforeKey     given-name part when paired with keyNames (mutually exclusive with the
 *                           other name fields)
 * @param keyNames           family-name part when paired with namesBeforeKey
 * @param personName         a natural-order composed name, alone or paired with personNameInverted
 * @param personNameInverted an inverted-order name ("Family, Given"), alone or paired with
 *                           personName
 */
public record MedraContributor(
        @Nullable String role,
        @Nullable String namesBeforeKey,
        @Nullable String keyNames,
        @Nullable String personName,
        @Nullable String personNameInverted) {
}
