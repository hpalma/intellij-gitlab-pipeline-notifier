package org.hugopalma.pipelinenotifier.notify;

import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Server-supplied names end up in alerts, so what an alert may contain is worth pinning down. */
public class PipelineFailureTest {

    private static PipelineFailure failure(String ref, List<String> jobs) {
        PipelineRun run = new PipelineRun(7L, ref, "abcdef1234567890", "push", null, "https://github.com/o/r/actions/runs/7", null, "octocat");
        return new PipelineFailure(new RemoteProject("github", "github.com", "o/r"), run, jobs, "octocat", false);
    }

    @Test
    public void sanitizeDropsMarkupControlAndBidiCharacters() {
        assertEquals("abscript", PipelineFailure.sanitize("a<b>s\u0000c‮ript", 80));
        assertEquals("", PipelineFailure.sanitize(null, 80));
    }

    @Test
    public void sanitizeCapsLength() {
        assertEquals("abcde…", PipelineFailure.sanitize("abcdefghij", 5));
        assertEquals("abcde", PipelineFailure.sanitize("abcde", 5));
    }

    @Test
    public void plainSummaryCannotCarryMarkup() {
        String summary = failure("<a href=https://evil.example>click</a>", List.of("<b>deploy</b>")).plainSummary();

        assertFalse(summary.contains("<"));
        assertFalse(summary.contains(">"));
    }

    @Test
    public void htmlBodyIsEscapedAndLengthCapped() {
        String body = failure("x".repeat(500), List.of("a&b")).htmlBody();

        assertTrue(body.contains("a&amp;b"));
        assertFalse(body.contains("x".repeat(100)));
    }

    @Test
    public void jobListIsCapped() {
        List<String> jobs = List.of("a", "b", "c", "d", "e", "f", "g");

        assertTrue(failure("main", jobs).plainSummary().endsWith("a, b, c, d, e and 2 more"));
    }
}
