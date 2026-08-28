package org.hugopalma.gitlabpipelinenotifier.watch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemoteUrlParserTest {

    @Test
    public void parsesHttpsRemote() {
        RemoteProject project = RemoteUrlParser.parse("https://gitlab.com/group/sub/proj.git");
        assertEquals("gitlab.com", project.host());
        assertEquals("group/sub/proj", project.path());
    }

    @Test
    public void parsesHttpsRemoteWithoutGitSuffix() {
        RemoteProject project = RemoteUrlParser.parse("https://gitlab.com/group/proj");
        assertEquals("gitlab.com", project.host());
        assertEquals("group/proj", project.path());
    }

    @Test
    public void parsesScpLikeRemote() {
        RemoteProject project = RemoteUrlParser.parse("git@gitlab.com:group/sub/proj.git");
        assertEquals("gitlab.com", project.host());
        assertEquals("group/sub/proj", project.path());
    }

    @Test
    public void parsesSshUrlWithPort() {
        RemoteProject project = RemoteUrlParser.parse("ssh://git@gitlab.example.com:2222/group/proj.git");
        assertEquals("gitlab.example.com", project.host());
        assertEquals("group/proj", project.path());
    }

    @Test
    public void normalisesHostCaseAndTrailingSlash() {
        RemoteProject project = RemoteUrlParser.parse("https://GitLab.Example.COM/group/proj/");
        assertEquals("gitlab.example.com", project.host());
        assertEquals("group/proj", project.path());
    }

    @Test
    public void rejectsRemoteWithoutNamespace() {
        // A GitLab project always lives under at least one group, so a bare path is not one.
        assertNull(RemoteUrlParser.parse("https://gitlab.com/proj.git"));
    }

    @Test
    public void rejectsGarbage() {
        assertNull(RemoteUrlParser.parse(""));
        assertNull(RemoteUrlParser.parse(null));
        assertNull(RemoteUrlParser.parse("not a url"));
    }

    @Test
    public void rejectsBareHostWithNoPathAtAll() {
        assertNull(RemoteUrlParser.parse("https://gitlab.com"));
        assertNull(RemoteUrlParser.parse("https://gitlab.com/"));
    }

    @Test
    public void ignoresQueryStringAndFragment() {
        RemoteProject withQuery = RemoteUrlParser.parse("https://gitlab.com/group/proj.git?ref=main");
        assertEquals("group/proj", withQuery.path());

        RemoteProject withFragment = RemoteUrlParser.parse("https://gitlab.com/group/proj.git#readme");
        assertEquals("group/proj", withFragment.path());
    }

    @Test
    public void rejectsScpLikeRemoteWithoutPath() {
        // A trailing bare colon has no path component for the pattern to capture.
        assertNull(RemoteUrlParser.parse("git@gitlab.com:"));
    }

    @Test
    public void parsesScpLikeRemoteWithoutExplicitUser() {
        RemoteProject project = RemoteUrlParser.parse("gitlab.com:group/proj.git");
        assertEquals("gitlab.com", project.host());
        assertEquals("group/proj", project.path());
    }

    @Test
    public void derivesHostFromConfiguredUrl() {
        assertEquals("gitlab.com", RemoteUrlParser.hostOf("https://gitlab.com"));
        assertEquals("gitlab.com", RemoteUrlParser.hostOf("https://gitlab.com/"));
        assertEquals("gitlab.example.com", RemoteUrlParser.hostOf("gitlab.example.com"));
        assertNull(RemoteUrlParser.hostOf(""));
        assertNull(RemoteUrlParser.hostOf(null));
    }
}
