package org.hugopalma.pipelinenotifier.watch;

import org.hugopalma.pipelinenotifier.settings.NotificationRule;

import java.util.List;

/**
 * A distinct API query. Rules sharing a {@code username} collapse into one request, since
 * {@code username} is the only criterion the API can apply server-side for us.
 */
public record PollQuery(String username, List<NotificationRule> rules) {
}
