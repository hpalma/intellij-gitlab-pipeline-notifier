package org.hugopalma.pipelinenotifier.watch;

/** A project on one CI service: the provider, the server host, and the namespaced path. */
public record RemoteProject(String provider, String host, String path) {

    /** Stable key for watermark bookkeeping - survives project id changes and renames alike. */
    public String key() {
        return connectionKey(provider, host) + "|" + path;
    }

    /** Identifies the server this project lives on; {@link #key()} always starts with it. */
    public String connectionKey() {
        return connectionKey(provider, host);
    }

    public static String connectionKey(String provider, String host) {
        return provider + "|" + host;
    }
}
