package org.hugopalma.pipelinenotifier.notify;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.hugopalma.pipelinenotifier.watch.UrlSafety;

/** Opens server-supplied links only when they point at the expected server host over http(s). */
final class SafeBrowser {

    private static final Logger LOG = Logger.getInstance(SafeBrowser.class);

    private SafeBrowser() {
    }

    /** @param serverHost host of the connection the link came from, e.g. {@code gitlab.com} */
    static void browse(String url, String serverHost) {
        if (UrlSafety.isSafeToBrowse(url, serverHost)) {
            BrowserUtil.browse(url);
        } else {
            LOG.warn("Refusing to open pipeline link that is not an http(s) URL on the expected server host: "
                    + UrlSafety.redact(url));
        }
    }
}
