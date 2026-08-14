package org.skgif.doi.crossref;

import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Resolves a real Crossref-registered journal-level DOI for a journal's ISSN(s), via {@code
 * GET works?filter=type:journal,issn:<issn>} - Crossref does register {@code type: "journal"}
 * works for many journals themselves (verified live: ISSN 0028-0836/1476-4687 -&gt; DOI
 * {@code 10.1038/41586.1476-4687} for Nature), the same "prefer a real container DOI over an
 * otf id" idea {@code CrossrefToSkgIfMapper#venueFromXmlMetadata} already applies to
 * book/proceedings venues via the XML transform endpoint - this is the equivalent for plain
 * journal articles, via the REST API instead.
 *
 * <p>{@code @RequestScoped}, not application-wide: found DOIs are cached per ISSN only for the
 * lifetime of the current request, deliberately not longer - a journal's DOI doesn't actually
 * change, but "no journal-level DOI" can't be told apart from a transient failure (timeout,
 * network blip) from within a single lookup, so nothing is cached indefinitely across requests
 * where a fresh attempt might succeed. This still covers the main practical case: the
 * list/search endpoint mapping many articles from the same journal within one request, so that
 * page issues one lookup per distinct ISSN rather than one per article. This call is deliberately
 * made on both the single-item and list/search endpoints (unlike the XML enrichment, which is
 * single-item only to avoid N extra calls per page).
 */
@RequestScoped
public class CrossrefJournalDoiResolver {

    private final CrossrefClient crossrefClient;
    private final String mailto;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Inject
    public CrossrefJournalDoiResolver(@RestClient CrossrefClient crossrefClient,
            @ConfigProperty(name = "crossref.mailto") Optional<String> crossrefMailto) {
        this.crossrefClient = crossrefClient;
        this.mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);
    }

    /**
     * Tries each ISSN in turn (an article commonly carries both a print and an electronic ISSN)
     * and returns the first journal-level DOI found. Any lookup failure - non-2xx response,
     * network/timeout error, or no matching record - degrades to an empty result for that ISSN
     * rather than propagating, so callers can always fall back to their existing otf-id
     * behavior.
     *
     * @param issns the journal's ISSN(s) to try, in order
     * @return the first journal-level DOI found, or empty if none resolve
     */
    public Optional<String> resolveJournalDoi(List<String> issns) {
        if (issns == null) {
            return Optional.empty();
        }
        for (String issn : issns) {
            if (issn == null || issn.isBlank()) {
                continue;
            }
            // computeIfAbsent never stores a null mapping function result, so a miss/failure
            // (fetchJournalDoi returning null) is naturally retried next time rather than cached.
            String doi = cache.computeIfAbsent(issn, this::fetchJournalDoi);
            if (doi != null) {
                return Optional.of(doi);
            }
        }
        return Optional.empty();
    }

    private String fetchJournalDoi(String issn) {
        try {
            CrossrefWorkListResponse response = crossrefClient.listWorks(
                    "type:journal,issn:" + issn, null, null, 1, null, mailto);
            if (response == null || response.message == null || response.message.items == null
                    || response.message.items.isEmpty()) {
                return null;
            }
            CrossrefWork journal = response.message.items.get(0);
            return journal.doi;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
