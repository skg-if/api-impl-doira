package org.skgif.doi.medra.dto;

/**
 * One ONIX-for-DOI {@code <Contributor>} element. {@code role} is the raw {@code ContributorRole}
 * code (e.g. {@code "A01"} for author - the only value observed in practice). The four name
 * fields are mutually exclusive in practice but all nullable here - a record carries exactly one
 * of {@code namesBeforeKey}+{@code keyNames}, {@code personName} (alone or with {@code
 * personNameInverted}), or {@code personNameInverted} alone - see {@code
 * MedraToSkgIfMapper#personRef} for the precedence used to resolve them into a display name.
 */
public record MedraContributor(
        String role,
        String namesBeforeKey,
        String keyNames,
        String personName,
        String personNameInverted) {
}
