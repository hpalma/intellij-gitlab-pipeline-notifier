package org.hugopalma.pipelinenotifier.gitlab;

import org.hugopalma.pipelinenotifier.provider.CiClient;
import org.hugopalma.pipelinenotifier.provider.CiProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GitLabProvider implements CiProvider {

    /** Also the default provider for a new rule. */
    public static final String ID = "gitlab";

    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("push", "Push");
        SOURCES.put("merge_request_event", "Merge request");
        SOURCES.put("schedule", "Schedule");
        SOURCES.put("web", "Web");
        SOURCES.put("trigger", "Trigger");
        SOURCES.put("api", "API");
        SOURCES.put("pipeline", "Multi-project");
        SOURCES.put("parent_pipeline", "Child pipeline");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "GitLab";
    }

    @Override
    public String defaultHost() {
        return "https://gitlab.com";
    }

    @Override
    public String tokenHint() {
        return "Personal access token with the read_api scope. Stored in the IDE password safe.";
    }

    @Override
    public String userLabel() {
        return "GitLab username";
    }

    @Override
    public Map<String, String> sourceChoices() {
        return SOURCES;
    }

    @Override
    public CiClient createClient(String host, String token) {
        return new GitLabCiClient(new GitLabClient(host, token));
    }
}
