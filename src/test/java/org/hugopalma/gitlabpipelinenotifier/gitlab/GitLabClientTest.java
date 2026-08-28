package org.hugopalma.gitlabpipelinenotifier.gitlab;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabJob;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabUser;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Parsing tests over recorded GitLab response shapes - no network involved. */
public class GitLabClientTest {

    private final Gson gson = new Gson();

    @Test
    public void parsesUser() {
        GitLabUser user = gson.fromJson(
                "{\"id\":42,\"username\":\"hugo\",\"name\":\"Hugo Palma\"}", GitLabUser.class);
        assertEquals(42L, user.id());
        assertEquals("hugo", user.username());
    }

    @Test
    public void parsesPipelineListEntry() {
        Type listType = new TypeToken<List<GitLabPipeline>>() { }.getType();
        List<GitLabPipeline> pipelines = gson.fromJson("""
                [{
                  "id": 1234567,
                  "iid": 12,
                  "project_id": 99,
                  "status": "failed",
                  "source": "merge_request_event",
                  "ref": "feature/thing",
                  "sha": "0123456789abcdef0123456789abcdef01234567",
                  "web_url": "https://gitlab.com/g/p/-/pipelines/1234567",
                  "created_at": "2026-08-21T10:00:00.000Z",
                  "updated_at": "2026-08-21T10:05:00.000Z"
                }]
                """, listType);

        GitLabPipeline pipeline = pipelines.getFirst();
        assertEquals(1234567L, pipeline.id());
        assertEquals("failed", pipeline.status());
        assertEquals("merge_request_event", pipeline.source());
        assertEquals("feature/thing", pipeline.ref());
        assertEquals("01234567", pipeline.shortSha());
        assertEquals(Instant.parse("2026-08-21T10:05:00Z"), pipeline.updatedAtInstant());
        // The list endpoint never populates the triggering user - that is why the client filters
        // by username server-side rather than in code.
        assertNull(pipeline.user());
    }

    @Test
    public void parsesPipelineDetailWithUser() {
        GitLabPipeline pipeline = gson.fromJson("""
                {
                  "id": 7,
                  "status": "failed",
                  "source": "push",
                  "ref": "main",
                  "sha": "abcdef1234567890",
                  "web_url": "https://gitlab.com/g/p/-/pipelines/7",
                  "updated_at": "2026-08-21T10:05:00.000+00:00",
                  "duration": 321,
                  "user": {"id": 42, "username": "hugo", "name": "Hugo Palma"}
                }
                """, GitLabPipeline.class);

        assertEquals("hugo", pipeline.user().username());
        assertEquals(Integer.valueOf(321), pipeline.duration());
        // GitLab emits both 'Z' and '+00:00' offsets depending on endpoint and version.
        assertEquals(Instant.parse("2026-08-21T10:05:00Z"), pipeline.updatedAtInstant());
    }

    @Test
    public void parsesJobsAndIgnoresUnknownFields() {
        Type listType = new TypeToken<List<GitLabJob>>() { }.getType();
        List<GitLabJob> jobs = gson.fromJson("""
                [
                  {"id": 1, "name": "test:unit", "stage": "test", "status": "failed",
                   "allow_failure": false, "web_url": "https://gitlab.com/j/1",
                   "some_future_field": {"nested": true}},
                  {"id": 2, "name": "lint", "stage": "test", "status": "failed", "allow_failure": true}
                ]
                """, listType);

        assertEquals(2, jobs.size());
        assertEquals("test:unit", jobs.get(0).name());
        assertTrue(jobs.get(1).allowFailure());
    }

    @Test
    public void unparseableTimestampsDoNotThrow() {
        assertNull(GitLabPipeline.parseTimestamp("not a date"));
        assertNull(GitLabPipeline.parseTimestamp(null));
        assertNull(GitLabPipeline.parseTimestamp(""));
    }
}
