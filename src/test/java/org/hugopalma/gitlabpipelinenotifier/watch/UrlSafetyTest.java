package org.hugopalma.gitlabpipelinenotifier.watch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UrlSafetyTest {

    @Test
    public void redactStripsUserinfo() {
        assertEquals("https://gitlab.com/g/p.git", UrlSafety.redact("https://user:glpat-xxxx@gitlab.com/g/p.git"));
        assertEquals("git@gitlab.com:g/p.git", UrlSafety.redact("git@gitlab.com:g/p.git"));
    }

    @Test
    public void normalizeDefaultsToHttpsAndStripsSlashes() {
        assertEquals("https://gitlab.com", UrlSafety.normalizeBaseUrl(" gitlab.com/ "));
        assertEquals("http://127.0.0.1:8080", UrlSafety.normalizeBaseUrl("http://127.0.0.1:8080"));
    }

    @Test
    public void normalizeRejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.normalizeBaseUrl(""));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.normalizeBaseUrl("ftp://gitlab.com"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.normalizeBaseUrl("https://u:p@gitlab.com"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.normalizeBaseUrl("https://gitlab.com?x=1"));
    }

    @Test
    public void browseOnlyConfiguredHostOverHttp() {
        String cfg = "https://gitlab.com";
        assertTrue(UrlSafety.isSafeToBrowse("https://gitlab.com/g/p/-/pipelines/1", cfg));
        assertFalse(UrlSafety.isSafeToBrowse("https://evil.example/g/p", cfg));
        assertFalse(UrlSafety.isSafeToBrowse("file:///etc/passwd", cfg));
        assertFalse(UrlSafety.isSafeToBrowse(null, cfg));
    }
}
