package org.hugopalma.gitlabpipelinenotifier.notify;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.hugopalma.gitlabpipelinenotifier.settings.Settings;
import org.hugopalma.gitlabpipelinenotifier.watch.UrlSafety;

/** Opens server-supplied links only when they point at the configured GitLab host over http(s). */
final class SafeBrowser {

    private static final Logger LOG = Logger.getInstance(SafeBrowser.class);

    private SafeBrowser() {
    }

    static void browse(String url) {
        if (UrlSafety.isSafeToBrowse(url, Settings.getInstance().getState().gitlabHost)) {
            BrowserUtil.browse(url);
        } else {
            LOG.warn("Refusing to open pipeline link that is not an http(s) URL on the configured GitLab host: "
                    + UrlSafety.redact(url));
        }
    }
}
