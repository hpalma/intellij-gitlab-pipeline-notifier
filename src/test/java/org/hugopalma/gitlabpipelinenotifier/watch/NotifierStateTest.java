package org.hugopalma.gitlabpipelinenotifier.watch;

import org.hugopalma.gitlabpipelinenotifier.settings.NotifierState;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bookkeeping that stops the poller replaying or duplicating failures. */
public class NotifierStateTest {

    private static final String KEY = "gitlab.com|group/proj";

    @Test
    public void firstSightSeedsWatermarkToNowSoHistoryIsNotReplayed() {
        NotifierState state = new NotifierState();
        Instant now = Instant.parse("2026-08-21T10:00:00Z");

        assertEquals(now, state.watermarkFor(KEY, now));
        // A later call must return the seeded value, not the new "now".
        assertEquals(now, state.watermarkFor(KEY, Instant.parse("2026-08-21T11:00:00Z")));
    }

    @Test
    public void watermarkOnlyMovesForward() {
        NotifierState state = new NotifierState();
        Instant start = Instant.parse("2026-08-21T10:00:00Z");
        state.watermarkFor(KEY, start);

        state.advanceWatermark(KEY, Instant.parse("2026-08-21T10:30:00Z"));
        assertEquals(Instant.parse("2026-08-21T10:30:00Z"), state.watermarkFor(KEY, start));

        // An out-of-order response must not rewind the watermark and re-deliver everything since.
        state.advanceWatermark(KEY, Instant.parse("2026-08-21T10:15:00Z"));
        assertEquals(Instant.parse("2026-08-21T10:30:00Z"), state.watermarkFor(KEY, start));
    }

    @Test
    public void retriedPipelineIsNotAlertedTwice() {
        NotifierState state = new NotifierState();

        assertTrue(state.markAlerted(KEY, 1234L));
        // GitLab bumps updated_at when a failed pipeline is retried, so it comes back through the
        // watermark window; the id check is what stops a second alert.
        assertFalse(state.markAlerted(KEY, 1234L));
    }

    @Test
    public void withRetryAlertsOnEachFailedRunButStillOncePerRun() {
        NotifierState state = new NotifierState();

        assertTrue(state.markAlerted(KEY, 1234L, "2026-08-21T10:00:00Z"));
        // The same unchanged pipeline comes back every tick, because the poll window is inclusive
        // of the watermark. Only a retry moves updated_at, and only that may alert again.
        assertFalse(state.markAlerted(KEY, 1234L, "2026-08-21T10:00:00Z"));
        assertTrue(state.markAlerted(KEY, 1234L, "2026-08-21T10:05:00Z"));
    }

    @Test
    public void turningRetryAlertsOffAgainRestoresOncePerPipeline() {
        NotifierState state = new NotifierState();

        assertTrue(state.markAlerted(KEY, 1234L));
        // Switching the setting on changes the key, so the pipeline gets one more alert - the price
        // of not tracking which revision an id-only entry stood for.
        assertTrue(state.markAlerted(KEY, 1234L, "2026-08-21T10:00:00Z"));
        // Switching it back off must return to the suppressed id-only key, not start over.
        assertFalse(state.markAlerted(KEY, 1234L));
    }

    @Test
    public void differentProjectsDoNotShareDedupeKeys() {
        NotifierState state = new NotifierState();
        assertTrue(state.markAlerted("gitlab.com|a/b", 1L));
        assertTrue(state.markAlerted("gitlab.com|c/d", 1L));
    }

    @Test
    public void alertedListIsBounded() {
        NotifierState state = new NotifierState();
        for (int i = 0; i < NotifierState.MAX_ALERTED + 50; i++) {
            state.markAlerted(KEY, i);
        }
        assertEquals(NotifierState.MAX_ALERTED, state.getState().alertedPipelines.size());
        // The oldest entries are the ones evicted.
        assertTrue(state.markAlerted(KEY, 0L));
        assertFalse(state.markAlerted(KEY, NotifierState.MAX_ALERTED + 49L));
    }

    @Test
    public void resetClearsEverything() {
        NotifierState state = new NotifierState();
        state.watermarkFor(KEY, Instant.parse("2026-08-21T10:00:00Z"));
        state.markAlerted(KEY, 1L);
        state.setResolvedUsername("hugo");

        state.reset();

        assertTrue(state.getState().watermarks.isEmpty());
        assertTrue(state.getState().alertedPipelines.isEmpty());
        assertEquals(null, state.getResolvedUsername());
    }
}
