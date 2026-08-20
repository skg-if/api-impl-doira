package org.skgif.doi.crossref;

import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Resolves a real Crossref-registered journal-level DOI for a journal's ISSN(s), via {@code
 * GET works?filter=type:journal,issn:<issn>} - Crossref does register {@code type: "journal"}
 * works for many journals themselves (verified live: ISSN 0028-0836/1476-4687 -&gt; DOI
 * {@code 10.1038/41586.1476-4687} for Nature), the same "prefer a real container DOI over an
 * otf id" idea {@code CrossrefBiblioMapper#venueFromXmlMetadata} already applies to
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

    /**
     * @param crossrefClient the Crossref REST client used to look up journal-level DOIs
     * @param crossrefMailto contact email for Crossref's polite-pool API access, if configured
     */
    @Inject
    public CrossrefJournalDoiResolver(@RestClient CrossrefClient crossrefClient,
            @ConfigProperty(name = "crossref.mailto") Optional<String> crossrefMailto) {
        this.crossrefClient = crossrefClient;
        this.mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);
    }

    /**
     * Looks up each ISSN concurrently (an article commonly carries both a print and an electronic
     * ISSN, and each lookup is a slow live Crossref round-trip) and returns the journal-level DOI
     * for the first ISSN, in list order, that resolves - so wall-clock cost is bounded by the
     * slowest single lookup rather than their sum, while the result is unchanged from trying them
     * one at a time. Any lookup failure - non-2xx response, network/timeout error, or no matching
     * record - degrades to an empty result for that ISSN rather than propagating, so callers can
     * always fall back to their existing otf-id behavior.
     *
     * @param issns the journal's ISSN(s) to try, in order
     * @return the first journal-level DOI found, or empty if none resolve
     */
    // A per-call virtual-thread executor for at most a handful of short-lived HTTP lookups is the
    // JDK 21-idiomatic fan-out/fan-in shape (fully awaited via future.get() below before the
    // try-with-resources closes it, never left running past this method) - not the kind of
    // unmanaged, long-lived thread pool PMD's J2EE-compliance rule is meant to catch.
    @SuppressWarnings("PMD.DoNotUseThreads")
    public Optional<String> resolveJournalDoi(List<String> issns) {
        if (issns == null) {
            return Optional.empty();
        }
        List<String> candidates =
                issns.stream().filter(issn -> issn != null && !issn.isBlank()).distinct().toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // computeIfAbsent never stores a null mapping function result, so a miss/failure
        // (fetchJournalDoi returning Optional.empty(), unwrapped to null right here at the
        // computeIfAbsent boundary) is naturally retried next time rather than cached.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = candidates.stream()
                    .map(issn -> executor.submit(() -> cache.computeIfAbsent(issn,
                            i -> fetchJournalDoi(i).orElse(null))))
                    .toList();
            for (Future<String> future : futures) {
                try {
                    String doi = future.get();
                    if (doi != null) {
                        return Optional.of(doi);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                } catch (ExecutionException e) {
                    // fetchJournalDoi already swallows RuntimeException and returns null for this
                    // ISSN - this only guards an unexpected Error, so just move on to the next one.
                    continue;
                }
            }
            return Optional.empty();
        }
    }

    private Optional<String> fetchJournalDoi(String issn) {
        try {
            CrossrefWorkListResponse response = crossrefClient.listWorks(
                    "type:journal,issn:" + issn, null, null, 1, null, mailto);
            if (response == null || response.message() == null || response.message().items() == null ||
                    response.message().items().isEmpty()) {
                return Optional.empty();
            }
            CrossrefWork journal = response.message().items().getFirst();
            return Optional.ofNullable(journal.doi());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
