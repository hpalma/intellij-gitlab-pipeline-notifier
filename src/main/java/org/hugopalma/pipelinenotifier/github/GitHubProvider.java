package org.hugopalma.pipelinenotifier.github;

import org.hugopalma.pipelinenotifier.provider.CiClient;
import org.hugopalma.pipelinenotifier.provider.CiProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** GitHub Actions, on github.com or GitHub Enterprise Server. */
public final class GitHubProvider implements CiProvider {

    public static final String ID = "github";

    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("push", "Push");
        SOURCES.put("pull_request", "Pull request");
        SOURCES.put("schedule", "Schedule");
        SOURCES.put("workflow_dispatch", "Manual");
        SOURCES.put("repository_dispatch", "API dispatch");
        SOURCES.put("workflow_run", "Workflow run");
        SOURCES.put("merge_group", "Merge queue");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public String defaultHost() {
        return "https://github.com";
    }

    @Override
    public String tokenHint() {
        return "Personal access token with read access to Actions (fine-grained), or the repo scope"
                + " (classic). Stored in the IDE password safe.";
    }

    @Override
    public String userLabel() {
        return "GitHub login";
    }

    @Override
    public boolean pathsCaseSensitive() {
        return false;
    }

    @Override
    public Map<String, String> sourceChoices() {
        return SOURCES;
    }

    @Override
    public CiClient createClient(String host, String token) {
        return new GitHubClient(host, token);
    }
}
