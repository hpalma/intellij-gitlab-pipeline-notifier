package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bookkeeping the poller needs to survive an IDE restart without replaying old failures.
 *
 * <p>Deliberately separate from {@link Settings} and non-roaming: watermarks are meaningless on
 * another machine, and roaming them would let one machine's polling suppress another's.
 */
@Service
@State(
        name = "org.hugopalma.gitlabpipelinenotifier.settings.NotifierState",
        storages = @Storage(value = "GitLabPipelineNotifierState.xml", roamingType = RoamingType.DISABLED)
)
public final class NotifierState implements PersistentStateComponent<NotifierState.State> {

    /** Upper bound on remembered pipeline ids - enough to cover a busy poll, bounded for storage. */
    public static final int MAX_ALERTED = 500;

    private final State state = new State();

    public static class State {
        /** Username resolved from the token via {@code GET /user}, cached to avoid a call on start. */
        public String resolvedUsername;

        /** Project key to ISO-8601 instant of the newest pipeline update already processed. */
        public Map<String, String> watermarks = new LinkedHashMap<>();

        /** FIFO of {@code projectKey#pipelineId} already alerted on, capped at {@link #MAX_ALERTED}. */
        @XCollection(elementName = "pipeline")
        public List<String> alertedPipelines = new ArrayList<>();
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    /**
     * The point in time to poll from for {@code projectKey}.
     *
     * <p>On first sight of a project the watermark is seeded to <em>now</em>, so enabling the plugin
     * never dumps a backlog of historical failures on the user.
     */
    public synchronized Instant watermarkFor(String projectKey, Instant now) {
        String existing = state.watermarks.get(projectKey);
        if (existing != null) {
            try {
                return Instant.parse(existing);
            } catch (RuntimeException ignored) {
                // Corrupt value; fall through and reseed.
            }
        }
        state.watermarks.put(projectKey, now.toString());
        return now;
    }

    public synchronized void advanceWatermark(String projectKey, Instant to) {
        String raw = state.watermarks.get(projectKey);
        Instant current = null;
        if (raw != null) {
            try {
                current = Instant.parse(raw);
            } catch (RuntimeException ignored) {
                // Treat unparseable as absent.
            }
        }
        if (current == null || to.isAfter(current)) {
            state.watermarks.put(projectKey, to.toString());
        }
    }

    /**
     * Records an alert and reports whether it is new.
     *
     * <p>Needed on top of the watermark because GitLab bumps {@code updated_at} when a failed
     * pipeline is retried, which would otherwise re-deliver a pipeline the user has already been
     * shouted at about.
     */
    public synchronized boolean markAlerted(String projectKey, long pipelineId) {
        return markAlerted(projectKey, pipelineId, null);
    }

    /**
     * As {@link #markAlerted(String, long)}, but scoped to a single <em>run</em> of the pipeline.
     *
     * <p>{@code revision} is a marker that changes when the pipeline is retried - in practice its
     * {@code updated_at}. Passing one makes each failed run of the same pipeline id alertable once;
     * passing {@code null} collapses every run into a single alert. It cannot simply always be
     * included: the poll window is inclusive of the watermark, so the same unchanged pipeline comes
     * back on the next tick and only an identical key keeps it quiet.
     */
    public synchronized boolean markAlerted(String projectKey, long pipelineId, String revision) {
        String key = projectKey + "#" + pipelineId + (revision == null ? "" : "@" + revision);
        if (state.alertedPipelines.contains(key)) {
            return false;
        }
        state.alertedPipelines.add(key);
        while (state.alertedPipelines.size() > MAX_ALERTED) {
            state.alertedPipelines.removeFirst();
        }
        return true;
    }

    /** Drops all bookkeeping - used when the host or token changes and old keys are meaningless. */
    public synchronized void reset() {
        state.watermarks.clear();
        state.alertedPipelines.clear();
        state.resolvedUsername = null;
    }

    public synchronized String getResolvedUsername() {
        return state.resolvedUsername;
    }

    public synchronized void setResolvedUsername(String username) {
        state.resolvedUsername = username;
    }

    public static NotifierState getInstance() {
        return ApplicationManager.getApplication().getService(NotifierState.class);
    }
}
