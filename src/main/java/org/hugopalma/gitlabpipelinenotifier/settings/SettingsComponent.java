package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.hugopalma.gitlabpipelinenotifier.gitlab.GitLabClient;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LabelUI;
import javax.swing.table.AbstractTableModel;

/**
 * The settings form. Plain Swing with {@link FormBuilder} plus a {@link ToolbarDecorator} table for
 * the rules - the Kotlin UI DSL is not usable from Java.
 */
public class SettingsComponent {

    private final JBTextField gitlabHost = new JBTextField();
    private final JBPasswordField token = new JBPasswordField();
    private final JBLabel connectionResult = new JBLabel(" ");
    private final JBTextField pollInterval = new JBTextField();
    private final JBCheckBox watchGitRemotes = new JBCheckBox("Watch projects matching the git remotes of open projects");
    private final JBTextArea extraProjects = new JBTextArea(4, 40);

    private final JBCheckBox notifyOwnFailures = new JBCheckBox("Alert me when a pipeline I triggered fails");
    private final JBCheckBox ownStickyBalloon = new JBCheckBox("Sticky balloon and application icon badge");
    private final JBCheckBox ownSystemNotification = new JBCheckBox("System notification");
    private final JBCheckBox ownModalDialog = new JBCheckBox("Modal dialog");

    private final JBCheckBox alertOnRetries = new JBCheckBox("Alert again when a retried pipeline fails again");

    private final List<NotificationRule> rules = new ArrayList<>();
    private final RulesTableModel rulesModel = new RulesTableModel();
    private final JBTable rulesTable = new JBTable(rulesModel);

    private final JPanel mainPanel;

    public SettingsComponent() {
        extraProjects.setLineWrap(false);

        JButton testConnection = new JButton("Test connection");
        testConnection.addActionListener(event -> testConnection());

        JPanel tokenRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        tokenRow.add(token, BorderLayout.CENTER);
        tokenRow.add(testConnection, BorderLayout.EAST);

        notifyOwnFailures.addChangeListener(event -> updateOwnChannelsEnabled());

        JPanel ownChannels = new JPanel();
        ownChannels.setLayout(new BoxLayout(ownChannels, BoxLayout.Y_AXIS));
        ownChannels.setBorder(JBUI.Borders.emptyLeft(20));
        ownChannels.add(ownStickyBalloon);
        ownChannels.add(ownSystemNotification);
        ownChannels.add(ownModalDialog);

        rulesTable.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(520), JBUI.scale(120)));
        rulesTable.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(60));
        JPanel rulesPanel = ToolbarDecorator.createDecorator(rulesTable)
                .setAddAction(button -> editRule(null))
                .setEditAction(button -> editRule(selectedRule()))
                .setRemoveAction(button -> removeSelectedRule())
                .createPanel();

        JScrollPane extraProjectsScroll = new JScrollPane(extraProjects);
        extraProjectsScroll.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(80)));

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("GitLab URL:", gitlabHost, 1, false)
                .addComponent(new CommentLabel("Base URL of your GitLab instance, e.g. https://gitlab.com"))
                .addLabeledComponent("Access token:", tokenRow, 1, false)
                .addComponent(new CommentLabel(
                        "Personal access token with the read_api scope. Stored in the IDE password safe."))
                .addComponent(connectionResult)
                .addLabeledComponent("Poll every (seconds):", pollInterval, 1, false)
                .addComponent(new CommentLabel("Minimum " + Settings.MIN_POLL_SECONDS + " seconds."))
                .addSeparator(UIUtil.DEFAULT_VGAP)
                .addComponent(watchGitRemotes)
                .addLabeledComponent("Also watch these projects:", extraProjectsScroll, 1, true)
                .addComponent(new CommentLabel("One project path per line, e.g. group/subgroup/project"))
                .addSeparator(UIUtil.DEFAULT_VGAP)
                .addComponent(notifyOwnFailures)
                .addComponent(ownChannels)
                .addSeparator(UIUtil.DEFAULT_VGAP)
                .addLabeledComponent("Also alert me about:", rulesPanel, 1, true)
                .addComponent(new CommentLabel(
                        "Rules for other people's pipelines. Each rule picks its own alert channels."))
                .addSeparator(UIUtil.DEFAULT_VGAP)
                .addComponent(alertOnRetries)
                .addComponent(new CommentLabel(
                        "Off: one alert per pipeline, however many times it is retried. "
                                + "On: every failed run of it alerts again."))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private void updateOwnChannelsEnabled() {
        boolean enabled = notifyOwnFailures.isSelected();
        ownStickyBalloon.setEnabled(enabled);
        ownSystemNotification.setEnabled(enabled);
        ownModalDialog.setEnabled(enabled);
    }

    /**
     * Validates the credentials by resolving the token's own user.
     *
     * <p>Runs on a pooled thread: it touches both the OS keychain and the network, neither of which
     * may block the EDT.
     */
    private void testConnection() {
        String host = gitlabHost.getText().trim();
        String secret = new String(token.getPassword());

        if (host.isEmpty() || secret.isEmpty()) {
            showResult("Enter a GitLab URL and an access token first.", false);
            return;
        }

        showResult("Connecting...", true);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String message;
            boolean ok;
            try {
                String username = new GitLabClient(host, secret).currentUser().username();
                message = "Connected as " + username;
                ok = true;
            } catch (Exception e) {
                message = "Failed: " + e.getMessage();
                ok = false;
            }
            String finalMessage = message;
            boolean finalOk = ok;
            SwingUtilities.invokeLater(() -> showResult(finalMessage, finalOk));
        });
    }

    private void showResult(String message, boolean ok) {
        connectionResult.setText(message);
        connectionResult.setForeground(ok
                ? UIUtil.getLabelSuccessForeground()
                : JBUI.CurrentTheme.Label.errorForeground());
    }

    private NotificationRule selectedRule() {
        int row = rulesTable.getSelectedRow();
        return row < 0 ? null : rules.get(rulesTable.convertRowIndexToModel(row));
    }

    private void editRule(NotificationRule existing) {
        NotificationRule working = existing == null ? new NotificationRule() : new NotificationRule(existing);
        RuleDialog dialog = new RuleDialog(working);
        if (!dialog.showAndGet()) {
            return;
        }
        dialog.applyTo(working);

        if (existing == null) {
            rules.add(working);
        } else {
            rules.set(rules.indexOf(existing), working);
        }
        rulesModel.fireTableDataChanged();
    }

    private void removeSelectedRule() {
        NotificationRule selected = selectedRule();
        if (selected != null) {
            rules.remove(selected);
            rulesModel.fireTableDataChanged();
        }
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return gitlabHost;
    }

    public String getGitlabHost() {
        return gitlabHost.getText().trim();
    }

    public void setGitlabHost(String value) {
        gitlabHost.setText(value == null ? "" : value);
    }

    public String getToken() {
        return new String(token.getPassword());
    }

    public void setToken(String value) {
        token.setText(value == null ? "" : value);
    }

    /** Falls back to the default rather than rejecting input, so a stray keystroke cannot block Apply. */
    public int getPollIntervalSeconds() {
        try {
            return Math.max(Integer.parseInt(pollInterval.getText().trim()), Settings.MIN_POLL_SECONDS);
        } catch (NumberFormatException e) {
            return Settings.DEFAULT_POLL_SECONDS;
        }
    }

    public void setPollIntervalSeconds(int value) {
        pollInterval.setText(String.valueOf(value));
    }

    public boolean isWatchGitRemotes() {
        return watchGitRemotes.isSelected();
    }

    public void setWatchGitRemotes(boolean value) {
        watchGitRemotes.setSelected(value);
    }

    public List<String> getExtraProjectPaths() {
        return Arrays.stream(extraProjects.getText().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public void setExtraProjectPaths(List<String> value) {
        extraProjects.setText(value == null ? "" : String.join("\n", value));
    }

    public boolean isNotifyOwnFailures() {
        return notifyOwnFailures.isSelected();
    }

    public void setNotifyOwnFailures(boolean value) {
        notifyOwnFailures.setSelected(value);
        updateOwnChannelsEnabled();
    }

    public boolean isOwnStickyBalloon() {
        return ownStickyBalloon.isSelected();
    }

    public void setOwnStickyBalloon(boolean value) {
        ownStickyBalloon.setSelected(value);
    }

    public boolean isOwnSystemNotification() {
        return ownSystemNotification.isSelected();
    }

    public void setOwnSystemNotification(boolean value) {
        ownSystemNotification.setSelected(value);
    }

    public boolean isOwnModalDialog() {
        return ownModalDialog.isSelected();
    }

    public void setOwnModalDialog(boolean value) {
        ownModalDialog.setSelected(value);
    }

    public boolean isAlertOnRetries() {
        return alertOnRetries.isSelected();
    }

    public void setAlertOnRetries(boolean value) {
        alertOnRetries.setSelected(value);
    }

    public List<NotificationRule> getRules() {
        return new ArrayList<>(rules);
    }

    public void setRules(List<NotificationRule> value) {
        rules.clear();
        if (value != null) {
            value.forEach(rule -> rules.add(new NotificationRule(rule)));
        }
        rulesModel.fireTableDataChanged();
    }

    private class RulesTableModel extends AbstractTableModel {

        private final String[] columns = {"On", "Matches", "Alerts"};

        @Override
        public int getRowCount() {
            return rules.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            NotificationRule rule = rules.get(row);
            return switch (column) {
                case 0 -> rule.enabled;
                case 1 -> rule.describe();
                default -> rule.describeChannels();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean enabled) {
                rules.get(row).enabled = enabled;
                fireTableRowsUpdated(row, row);
            }
        }
    }

    /** Small muted label used for the explanatory text under each field. */
    public static class CommentLabel extends JBLabel {

        public CommentLabel(String text) {
            super(text);
            setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        }

        @Override
        public void setUI(LabelUI ui) {
            super.setUI(ui);
            setFont(JBFont.medium());
        }
    }
}
