package org.hugopalma.pipelinenotifier.watch;

/**
 * What a git remote URL says: a host and a {@code group/subgroup/project} path. Which CI service
 * (if any) lives at that host is decided by the configured connections, not by the URL.
 */
public record RemoteLocation(String host, String path) {
}
