package org.hugopalma.gitlabpipelinenotifier.watch;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectDiscoveryTest {

    @Test
    public void readsUrlFromEveryRemoteSection() {
        String config = """
                [core]
                    repositoryformatversion = 0
                [remote "origin"]
                    url = git@gitlab.com:group/proj.git
                    fetch = +refs/heads/*:refs/remotes/origin/*
                [remote "upstream"]
                \turl=https://gitlab.com/other/proj.git
                """;
        assertEquals(List.of("git@gitlab.com:group/proj.git", "https://gitlab.com/other/proj.git"),
                List.copyOf(ProjectDiscovery.parseRemoteUrls(config)));
    }

    @Test
    public void ignoresPushUrlAndKeysOutsideRemoteSections() {
        String config = """
                [remote "origin"]
                    url = https://gitlab.com/a/b.git
                    pushurl = ssh://elsewhere/a/b.git
                    urlish = nope
                [branch "main"]
                    url = https://not-a-remote.example/x/y.git
                """;
        assertEquals(Set.of("https://gitlab.com/a/b.git"), ProjectDiscovery.parseRemoteUrls(config));
    }

    @Test
    public void handlesCrlfAndEmptyConfig() {
        assertEquals(Set.of("https://gitlab.com/a/b.git"),
                ProjectDiscovery.parseRemoteUrls("[remote \"o\"]\r\n\turl = https://gitlab.com/a/b.git\r\n"));
        assertTrue(ProjectDiscovery.parseRemoteUrls("").isEmpty());
    }
}
