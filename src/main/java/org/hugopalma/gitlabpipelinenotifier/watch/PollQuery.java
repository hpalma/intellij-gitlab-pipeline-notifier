package org.hugopalma.gitlabpipelinenotifier.watch;

import org.hugopalma.gitlabpipelinenotifier.settings.NotificationRule;

import java.util.List;

/**
 * A distinct API query. Rules sharing a {@code username} collapse into one request, since
 * {@code username} is the only criterion GitLab can apply server-side for us.
 */
public record PollQuery(String username, List<NotificationRule> rules) {
}
