package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.hugopalma.gitlabpipelinenotifier.watch.RuleMatcher;
import org.jetbrains.annotations.Nullable;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Add/edit dialog for one {@link NotificationRule}. */
public class RuleDialog extends DialogWrapper {

    /** Pipeline sources GitLab emits often enough to be worth offering as checkboxes. */
    private static final Map<String, String> COMMON_SOURCES = new LinkedHashMap<>();

    static {
        COMMON_SOURCES.put("push", "Push");
        COMMON_SOURCES.put("merge_request_event", "Merge request");
        COMMON_SOURCES.put("schedule", "Schedule");
        COMMON_SOURCES.put("web", "Web");
        COMMON_SOURCES.put("trigger", "Trigger");
        COMMON_SOURCES.put("api", "API");
        COMMON_SOURCES.put("pipeline", "Multi-project");
        COMMON_SOURCES.put("parent_pipeline", "Child pipeline");
    }

    private final JBTextField username = new JBTextField();
    private final JBTextField refGlob = new JBTextField();
    private final Map<String, JBCheckBox> sourceBoxes = new LinkedHashMap<>();
    private final JBCheckBox stickyBalloon = new JBCheckBox("Sticky balloon and application icon badge");
    private final JBCheckBox systemNotification = new JBCheckBox("System notification");
    private final JBCheckBox modalDialog = new JBCheckBox("Modal dialog");

    public RuleDialog(NotificationRule rule) {
        super(true);

        username.setText(rule.username == null ? "" : rule.username);
        refGlob.setText(rule.refGlob == null ? "" : rule.refGlob);
        for (Map.Entry<String, String> entry : COMMON_SOURCES.entrySet()) {
            sourceBoxes.put(entry.getKey(), new JBCheckBox(entry.getValue(), rule.sources.contains(entry.getKey())));
        }
        stickyBalloon.setSelected(rule.stickyBalloon);
        systemNotification.setSelected(rule.systemNotification);
        modalDialog.setSelected(rule.modalDialog);

        setTitle("Pipeline Notification Rule");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel sources = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        sourceBoxes.values().forEach(sources::add);

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Triggered by user:", username, 1, false)
                .addComponent(new SettingsComponent.CommentLabel(
                        "GitLab username. Leave empty to match any user."))
                .addLabeledComponent("Branch or tag:", refGlob, 1, false)
                .addComponent(new SettingsComponent.CommentLabel(
                        "Glob pattern, for example main or release/x. "
                                + "A single star stops at a slash, a double star crosses it. "
                                + "Leave empty to match any ref."))
                .addSeparator()
                .addLabeledComponent("Pipeline source:", sources, 1, false)
                .addComponent(new SettingsComponent.CommentLabel(
                        "Leave all unchecked to match any source."))
                .addSeparator()
                .addComponent(stickyBalloon)
                .addComponent(systemNotification)
                .addComponent(new SettingsComponent.CommentLabel(
                        "Shown by the operating system when the IDE is not focused."))
                .addComponent(modalDialog)
                .addComponent(new SettingsComponent.CommentLabel(
                        "Blocks the IDE until dismissed."))
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8));
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String glob = refGlob.getText().trim();
        if (!glob.isEmpty()) {
            try {
                RuleMatcher.globToRegex(glob);
            } catch (RuntimeException e) {
                return new ValidationInfo("Not a valid pattern: " + e.getMessage(), refGlob);
            }
        }
        if (!stickyBalloon.isSelected() && !systemNotification.isSelected() && !modalDialog.isSelected()) {
            return new ValidationInfo("Pick at least one way to be alerted.", stickyBalloon);
        }
        return null;
    }

    /** Applies the edited values onto {@code target}. Call only after {@code showAndGet()} was true. */
    public void applyTo(NotificationRule target) {
        target.username = emptyToNull(username.getText());
        target.refGlob = emptyToNull(refGlob.getText());

        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JBCheckBox> entry : sourceBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        target.sources = selected;

        target.stickyBalloon = stickyBalloon.isSelected();
        target.systemNotification = systemNotification.isSelected();
        target.modalDialog = modalDialog.isSelected();
    }

    private static String emptyToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
