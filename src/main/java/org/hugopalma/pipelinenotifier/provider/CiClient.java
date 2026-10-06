package org.hugopalma.pipelinenotifier.provider;

import org.hugopalma.pipelinenotifier.watch.RemoteProject;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Read-only access to one CI service, as the poller needs it. One instance is bound to one host
 * and token; implementations may cache (project lookups, say) for their own lifetime.
 *
 * <p>Every call blocks and must be made off the EDT. Authentication failures surface as
 * {@link CiAuthException}, other HTTP failures as {@link CiHttpException}.
 */
public interface CiClient {

    /** Login of the user the token belongs to. Also the cheapest way to validate a token. */
    String currentUser() throws IOException, InterruptedException;

    /**
     * Failed runs updated since {@code since}, newest first, one page at a time ({@code page} is
     * 1-based). {@code username} is applied server-side when non-blank. The caller keeps asking for
     * pages while {@link Page#hasMore()}, so a burst larger than one page is not silently truncated.
     */
    Page<PipelineRun> failedRuns(RemoteProject target, Instant since, String username, int perPage, int page)
            throws IOException, InterruptedException;

    /** The run with every field the listing may have left out, notably {@code triggeredBy}. */
    PipelineRun detail(RemoteProject target, PipelineRun run) throws IOException, InterruptedException;

    /** Names of the jobs that actually broke the run, so the alert can say what failed. */
    List<String> failedJobs(RemoteProject target, PipelineRun run) throws IOException, InterruptedException;

    /** Project paths the token's user can see, for the "watch" picker. */
    Page<String> listProjects(String search, int perPage, int page) throws IOException, InterruptedException;
}
