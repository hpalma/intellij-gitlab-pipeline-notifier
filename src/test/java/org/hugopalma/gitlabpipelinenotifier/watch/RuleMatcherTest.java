package org.hugopalma.gitlabpipelinenotifier.watch;

import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.gitlabpipelinenotifier.settings.NotificationRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleMatcherTest {

    private static GitLabPipeline pipeline(String ref, String source) {
        return new GitLabPipeline(1L, 1L, 1L, "failed", source, ref, "abcdef1234567890",
                null, "https://gitlab.com/x/-/pipelines/1", null, null, null, null);
    }

    private static NotificationRule rule(String username, String refGlob, List<String> sources) {
        NotificationRule rule = new NotificationRule();
        rule.username = username;
        rule.refGlob = refGlob;
        rule.sources = sources == null ? List.of() : sources;
        return rule;
    }

    @Test
    public void singleStarDoesNotCrossSlash() {
        assertTrue(RuleMatcher.globToRegex("release/*").matcher("release/1.2").matches());
        assertFalse(RuleMatcher.globToRegex("release/*").matcher("release/a/b").matches());
    }

    @Test
    public void doubleStarCrossesSlash() {
        assertTrue(RuleMatcher.globToRegex("release/**").matcher("release/a/b").matches());
    }

    @Test
    public void treatsRegexMetacharactersLiterally() {
        // A dot in a tag like v1.2.3 must not behave as "any character".
        assertTrue(RuleMatcher.globToRegex("v1.2.3").matcher("v1.2.3").matches());
        assertFalse(RuleMatcher.globToRegex("v1.2.3").matcher("v1X2X3").matches());
    }

    @Test
    public void questionMarkMatchesSingleChar() {
        assertTrue(RuleMatcher.globToRegex("v?").matcher("v1").matches());
        assertFalse(RuleMatcher.globToRegex("v?").matcher("v12").matches());
    }

    @Test
    public void emptyCriteriaMatchAnything() {
        AlertChannels channels = RuleMatcher.match(pipeline("main", "push"), List.of(rule(null, null, null)));
        assertTrue(channels.any());
    }

    @Test
    public void refGlobFiltersOutNonMatchingBranch() {
        AlertChannels channels = RuleMatcher.match(pipeline("feature/x", "push"),
                List.of(rule(null, "main", null)));
        assertFalse(channels.any());
    }

    @Test
    public void sourceFiltersOutNonMatchingSource() {
        AlertChannels channels = RuleMatcher.match(pipeline("main", "schedule"),
                List.of(rule(null, null, List.of("push"))));
        assertFalse(channels.any());
    }

    @Test
    public void disabledRuleNeverMatches() {
        NotificationRule disabled = rule(null, null, null);
        disabled.enabled = false;
        assertFalse(RuleMatcher.match(pipeline("main", "push"), List.of(disabled)).any());
    }

    @Test
    public void channelsFromMultipleMatchingRulesAreUnioned() {
        NotificationRule balloonOnly = rule(null, "main", null);
        balloonOnly.stickyBalloon = true;
        balloonOnly.systemNotification = false;
        balloonOnly.modalDialog = false;

        NotificationRule dialogOnly = rule(null, "**", null);
        dialogOnly.stickyBalloon = false;
        dialogOnly.systemNotification = false;
        dialogOnly.modalDialog = true;

        AlertChannels channels = RuleMatcher.match(pipeline("main", "push"), List.of(balloonOnly, dialogOnly));
        assertTrue(channels.stickyBalloon());
        assertTrue(channels.modalDialog());
        assertFalse(channels.systemNotification());
    }

    @Test
    public void rulesSharingUsernameCollapseIntoOneQuery() {
        // Otherwise N rules would mean N API requests per project per poll.
        List<PollQuery> queries = RuleMatcher.planQueries(List.of(
                rule("hugo", "main", null),
                rule("hugo", "develop", null),
                rule("someone-else", null, null),
                rule(null, null, null)));

        assertEquals(3, queries.size());
        assertEquals(2, queries.stream().filter(q -> "hugo".equals(q.username())).findFirst().orElseThrow()
                .rules().size());
    }

    @Test
    public void disabledRulesAreNotPlanned() {
        NotificationRule disabled = rule("hugo", null, null);
        disabled.enabled = false;
        assertTrue(RuleMatcher.planQueries(List.of(disabled)).isEmpty());
    }
}
