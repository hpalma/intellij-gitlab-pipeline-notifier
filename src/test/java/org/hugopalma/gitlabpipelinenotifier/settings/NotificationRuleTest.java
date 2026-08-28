package org.hugopalma.gitlabpipelinenotifier.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** {@link NotificationRule#describe()} and {@link NotificationRule#describeChannels()}. */
public class NotificationRuleTest {

    @Test
    public void describesEmptyRuleAsAnyFailedPipeline() {
        NotificationRule rule = new NotificationRule();
        assertEquals("any failed pipeline", rule.describe());
    }

    @Test
    public void describesBlankCriteriaAsAnyFailedPipeline() {
        // Blank strings are how the settings UI represents "unset", same as null.
        NotificationRule rule = new NotificationRule();
        rule.username = "   ";
        rule.refGlob = "";
        assertEquals("any failed pipeline", rule.describe());
    }

    @Test
    public void describesUsernameOnly() {
        NotificationRule rule = new NotificationRule();
        rule.username = "hugo";
        assertEquals("by hugo", rule.describe());
    }

    @Test
    public void describesRefGlobOnly() {
        NotificationRule rule = new NotificationRule();
        rule.refGlob = "release/*";
        assertEquals("on release/*", rule.describe());
    }

    @Test
    public void describesSourcesOnly() {
        NotificationRule rule = new NotificationRule();
        rule.sources.add("push");
        rule.sources.add("merge_request_event");
        assertEquals("from push/merge_request_event", rule.describe());
    }

    @Test
    public void describesAllCriteriaTogetherInOrder() {
        NotificationRule rule = new NotificationRule();
        rule.username = "hugo";
        rule.refGlob = "main";
        rule.sources.add("push");
        assertEquals("by hugo on main from push", rule.describe());
    }

    @Test
    public void describesNoChannelsAsNone() {
        NotificationRule rule = new NotificationRule();
        rule.stickyBalloon = false;
        rule.systemNotification = false;
        rule.modalDialog = false;
        assertEquals("none", rule.describeChannels());
    }

    @Test
    public void describesDefaultChannels() {
        // Balloon and system notification are on by default; the modal dialog is opt-in.
        NotificationRule rule = new NotificationRule();
        assertEquals("Balloon, System", rule.describeChannels());
    }

    @Test
    public void describesAllChannelsInOrder() {
        NotificationRule rule = new NotificationRule();
        rule.stickyBalloon = true;
        rule.systemNotification = true;
        rule.modalDialog = true;
        assertEquals("Balloon, System, Dialog", rule.describeChannels());
    }

    @Test
    public void copyConstructorDuplicatesAllFieldsIncludingSourcesList() {
        NotificationRule original = new NotificationRule();
        original.enabled = false;
        original.username = "hugo";
        original.refGlob = "main";
        original.sources.add("push");
        original.stickyBalloon = false;
        original.systemNotification = false;
        original.modalDialog = true;

        NotificationRule copy = new NotificationRule(original);
        assertEquals(original.describe(), copy.describe());
        assertEquals(original.describeChannels(), copy.describeChannels());

        // Mutating the copy's list must not affect the original - it must be a real copy.
        copy.sources.add("web");
        assertEquals(1, original.sources.size());
    }
}
