package org.skgif.doi.crossref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CrossrefJournalDoiResolver}, following the direct-construction Mockito
 * pattern already used by {@code CrossrefToSkgIfMapperVenueTest} - no CDI container needed.
 */
class CrossrefJournalDoiResolverTest {

    private static final int TEST_TIMEOUT_SECONDS = 5;

    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    private final CrossrefJournalDoiResolver resolver =
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty());

    private static CrossrefWorkListResponse listResponseWithDoi(String doi) {
        CrossrefWork work = new CrossrefWork(doi, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
        return new CrossrefWorkListResponse("ok", new CrossrefWorkListResponse.Message(1, List.of(work)));
    }

    private static CrossrefWorkListResponse emptyListResponse() {
        return new CrossrefWorkListResponse("ok", new CrossrefWorkListResponse.Message(0, List.of()));
    }

    @Test
    void resolvesToNullIssnList() {
        assertThat(resolver.resolveJournalDoi(null)).isEmpty();
        verifyNoInteractions(crossrefClient);
    }

    @Test
    void resolvesToEmptyForBlankOrEmptyIssns() {
        assertThat(resolver.resolveJournalDoi(List.of())).isEmpty();
        assertThat(resolver.resolveJournalDoi(List.of(" ", ""))).isEmpty();
        verifyNoInteractions(crossrefClient);
    }

    @Test
    void prefersFirstIssnInListOrderWhenBothResolve() {
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenReturn(listResponseWithDoi("10.1038/print-doi"));
        when(crossrefClient.listWorks(eq("type:journal,issn:1476-4687"), any(), any(), eq(1), any(), any()))
                .thenReturn(listResponseWithDoi("10.1038/electronic-doi"));

        Optional<String> resolved = resolver.resolveJournalDoi(List.of("0028-0836", "1476-4687"));

        assertThat(resolved).contains("10.1038/print-doi");
    }

    @Test
    void fallsBackToLaterIssnWhenEarlierOneDoesNotResolve() {
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenReturn(emptyListResponse());
        when(crossrefClient.listWorks(eq("type:journal,issn:1476-4687"), any(), any(), eq(1), any(), any()))
                .thenReturn(listResponseWithDoi("10.1038/electronic-doi"));

        Optional<String> resolved = resolver.resolveJournalDoi(List.of("0028-0836", "1476-4687"));

        assertThat(resolved).contains("10.1038/electronic-doi");
    }

    /**
     * Proves the ISSN lookups actually run concurrently rather than sequentially, without relying
     * on flaky wall-clock timing assertions: both stubbed lookups first count down a shared latch,
     * then wait on that same latch before returning. A strictly sequential implementation would
     * deadlock (the first call waits forever for the second, which never starts because the first
     * hasn't returned yet); a concurrent implementation lets both proceed and completes quickly.
     */
    @Test
    void looksUpIssnsConcurrentlyRatherThanSequentially() {
        CountDownLatch bothStarted = new CountDownLatch(2);

        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenAnswer(invocation -> {
                    bothStarted.countDown();
                    awaitOrFail(bothStarted);
                    return emptyListResponse();
                });
        when(crossrefClient.listWorks(eq("type:journal,issn:1476-4687"), any(), any(), eq(1), any(), any()))
                .thenAnswer(invocation -> {
                    bothStarted.countDown();
                    awaitOrFail(bothStarted);
                    return listResponseWithDoi("10.1038/electronic-doi");
                });

        Optional<String> resolved = assertTimeoutPreemptively(Duration.ofSeconds(TEST_TIMEOUT_SECONDS),
                () -> resolver.resolveJournalDoi(List.of("0028-0836", "1476-4687")));

        assertThat(resolved).contains("10.1038/electronic-doi");
    }

    // Restoring the interrupt flag on a test-helper thread (mirrors CrossrefJournalDoiResolver's
    // own InterruptedException handling being exercised here) is not the unmanaged, long-lived
    // thread pool PMD's J2EE-compliance rule is meant to catch.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static void awaitOrFail(CountDownLatch latch) {
        try {
            assertThat(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("both ISSN lookups should have started concurrently")
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for concurrent lookups", e);
        }
    }
}
