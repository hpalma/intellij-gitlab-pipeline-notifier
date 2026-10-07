package org.hugopalma.pipelinenotifier.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.hugopalma.pipelinenotifier.watch.RuleMatcher;
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

    private final JBTextField username = new JBTextField();
    private final JBTextField refGlob = new JBTextField();
    private final ComboBox<String> provider = new ComboBox<>(
            CiProviders.all().stream().map(CiProvider::displayName).toArray(String[]::new));
    private final JPanel sources = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final SettingsComponent.CommentLabel usernameHelp = new SettingsComponent.CommentLabel(" ");
    private final Map<String, JBCheckBox> sourceBoxes = new LinkedHashMap<>();
    private final List<String> initialSources;
    private final JBCheckBox stickyBalloon = new JBCheckBox("Sticky balloon and application icon badge");
    private final JBCheckBox systemNotification = new JBCheckBox("System notification");
    private final JBCheckBox modalDialog = new JBCheckBox("Modal dialog");

    public RuleDialog(NotificationRule rule) {
        super(true);

        username.setText(rule.username == null ? "" : rule.username);
        refGlob.setText(rule.refGlob == null ? "" : rule.refGlob);
        initialSources = new ArrayList<>(rule.sources);

        CiProvider current = CiProviders.find(rule.provider);
        provider.setSelectedIndex(current == null ? 0 : CiProviders.all().indexOf(current));
        // Sources are the provider's own vocabulary, so the choices follow the selected provider.
        provider.addActionListener(_ -> refreshProviderSpecifics());
        refreshProviderSpecifics();
        stickyBalloon.setSelected(rule.stickyBalloon);
        systemNotification.setSelected(rule.systemNotification);
        modalDialog.setSelected(rule.modalDialog);

        setTitle("Pipeline Notification Rule");
        init();
    }

    private CiProvider selectedProvider() {
        return CiProviders.all().get(provider.getSelectedIndex());
    }

    /** Rebuilds the source checkboxes for the selected provider, keeping ticks that still apply. */
    private void refreshProviderSpecifics() {
        List<String> ticked = sourceBoxes.isEmpty() ? initialSources : selectedSources();
        sourceBoxes.clear();
        sources.removeAll();
        for (Map.Entry<String, String> entry : selectedProvider().sourceChoices().entrySet()) {
            JBCheckBox box = new JBCheckBox(entry.getValue(), ticked.contains(entry.getKey()));
            sourceBoxes.put(entry.getKey(), box);
            sources.add(box);
        }
        usernameHelp.setText(selectedProvider().userLabel() + ". Leave empty to match any user.");
        sources.revalidate();
        sources.repaint();
    }

    private List<String> selectedSources() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JBCheckBox> entry : sourceBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Service:", provider, 1, false)
                .addLabeledComponent("Triggered by user:", username, 1, false)
                .addComponent(usernameHelp)
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

        target.provider = selectedProvider().id();
        target.sources = selectedSources();

        target.stickyBalloon = stickyBalloon.isSelected();
        target.systemNotification = systemNotification.isSelected();
        target.modalDialog = modalDialog.isSelected();
    }

    private static String emptyToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
