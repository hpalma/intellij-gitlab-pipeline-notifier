package org.hugopalma.pipelinenotifier.provider;

import java.util.Map;

/**
 * Describes one CI service and creates clients for it. To support another service, implement this
 * and a {@link CiClient}, then register it in {@link CiProviders}; settings, rules, polling and
 * alerting pick it up from there.
 */
public interface CiProvider {

    /** Stable identifier persisted in settings and state. Never change it once released. */
    String id();

    /** Shown in the UI, e.g. "GitLab". */
    String displayName();

    /** Pre-filled server URL, e.g. {@code https://gitlab.com}. */
    String defaultHost();

    /** One-line help under the access token field: what kind of token, with which scopes. */
    String tokenHint();

    /** Name for the person on this service, e.g. "GitLab username". */
    String userLabel();

    /**
     * Run origins worth offering as rule checkboxes, mapping the value found in
     * {@link PipelineRun#source()} to its label.
     */
    Map<String, String> sourceChoices();

    /** @throws IllegalArgumentException if {@code host} cannot be a server URL for this service */
    CiClient createClient(String host, String token);
}
