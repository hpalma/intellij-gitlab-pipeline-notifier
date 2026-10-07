package org.hugopalma.pipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.jetbrains.annotations.NotNull;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;

/**
 * The settings of one provider's connection: server URL, token, and the extra projects to watch.
 * Built entirely from what {@link CiProvider} says about itself, so a new provider needs no UI.
 */
final class ConnectionPanel {

    private final CiProvider provider;

    private final JBTextField host = new JBTextField();
    private final JBPasswordField token = new JBPasswordField();
    private final JBLabel connectionResult = new JBLabel(" ");
    private final JBLabel insecureWarning = new JBLabel(" ");
    private final AtomicInteger testSeq = new AtomicInteger();

    /**
     * The server the token in the field was loaded for, or {@code null} if the user typed it. A
     * loaded token is only ever sent to that server: editing the URL clears it, so a token for one
     * server is not silently carried over to another.
     */
    private String tokenHost;
    private boolean settingToken;

    private final List<String> extraProjectPaths = new ArrayList<>();
    private final DefaultListModel<String> extraProjectsListModel = new DefaultListModel<>();
    private final JBList<String> extraProjectsList = new JBList<>(extraProjectsListModel);

    private final JPanel panel;

    ConnectionPanel(CiProvider provider) {
        this.provider = provider;

        JButton testConnection = new JButton("Test connection");
        testConnection.addActionListener(_ -> testConnection());

        JPanel tokenRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        tokenRow.add(token, BorderLayout.CENTER);
        tokenRow.add(testConnection, BorderLayout.EAST);

        host.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                onHostEdited();
            }
        });
        token.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                if (!settingToken) {
                    tokenHost = null;
                }
            }
        });
        insecureWarning.setForeground(JBColor.ORANGE);

        extraProjectsList.setVisibleRowCount(4);
        extraProjectsList.getEmptyText().setText("No extra projects selected");
        JPanel extraProjectsPanel = ToolbarDecorator.createDecorator(extraProjectsList)
                .setAddAction(_ -> browseForProjects())
                .setRemoveAction(_ -> removeSelectedProjects())
                .disableUpDownActions()
                .createPanel();
        extraProjectsPanel.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(100)));

        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(provider.displayName() + " URL:", host, 1, false)
                .addComponent(new SettingsComponent.CommentLabel(
                        "Base URL of your " + provider.displayName() + " server, e.g. " + provider.defaultHost()))
                .addComponent(insecureWarning)
                .addLabeledComponent("Access token:", tokenRow, 1, false)
                .addComponent(new SettingsComponent.CommentLabel(provider.tokenHint()))
                .addComponent(connectionResult)
                .addLabeledComponent("Also watch these projects:", extraProjectsPanel, 1, true)
                .addComponent(new SettingsComponent.CommentLabel("Picked from the projects visible to your access token."))
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8));
    }

    CiProvider provider() {
        return provider;
    }

    JPanel getPanel() {
        return panel;
    }

    /**
     * Validates the credentials by resolving the token's own user.
     *
     * <p>Runs on a pooled thread: it touches the network, which may not block the EDT.
     */
    private void testConnection() {
        String serverUrl = getHost();
        String secret = getToken();

        if (serverUrl.isEmpty() || secret.isEmpty()) {
            showResult("Enter a " + provider.displayName() + " URL and an access token first.", false);
            return;
        }

        showResult("Connecting...", true);
        int mySeq = testSeq.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String message;
            boolean ok;
            try {
                message = "Connected as " + provider.createClient(serverUrl, secret).currentUser();
                ok = true;
            } catch (Exception e) {
                message = "Failed: " + e.getMessage();
                ok = false;
            }
            String finalMessage = message;
            boolean finalOk = ok;
            // A newer test, or an edit that invalidated this one, owns the label now.
            SwingUtilities.invokeLater(() -> {
                if (testSeq.get() == mySeq) {
                    showResult(finalMessage, finalOk);
                }
            });
        });
    }

    /** Opens the project picker and, on confirmation, replaces the extra-projects list with its result. */
    private void browseForProjects() {
        String serverUrl = getHost();
        String secret = getToken();

        if (serverUrl.isEmpty() || secret.isEmpty()) {
            Messages.showErrorDialog(panel,
                    "Enter a " + provider.displayName() + " URL and an access token first.", "Add Projects");
            return;
        }

        ProjectPickerDialog dialog = new ProjectPickerDialog(provider, serverUrl, secret, getExtraProjectPaths());
        if (dialog.showAndGet()) {
            setExtraProjectPaths(dialog.getSelectedPaths());
        }
    }

    private void removeSelectedProjects() {
        extraProjectsList.getSelectedValuesList().forEach(extraProjectPaths::remove);
        refreshExtraProjectsModel();
    }

    private void refreshExtraProjectsModel() {
        extraProjectsListModel.clear();
        extraProjectPaths.forEach(extraProjectsListModel::addElement);
    }

    private void showResult(String message, boolean ok) {
        connectionResult.setText(message);
        connectionResult.setForeground(ok
                ? UIUtil.getLabelSuccessForeground()
                : JBUI.CurrentTheme.Label.errorForeground());
    }

    JBTextField hostField() {
        return host;
    }

    String getHost() {
        return host.getText().trim();
    }

    void setHost(String value) {
        host.setText(value == null ? "" : value);
    }

    private void onHostEdited() {
        testSeq.incrementAndGet();
        boolean plainHttp = getHost().toLowerCase(Locale.ROOT).startsWith("http://");
        insecureWarning.setText(plainHttp
                ? "This URL uses http: the access token will be sent unencrypted."
                : " ");

        if (tokenHost != null && !getHost().equals(tokenHost) && !getToken().isEmpty()) {
            fillToken("");
            tokenHost = null;
            showResult("Token cleared because the URL changed. Enter the token for the new server.", false);
        }
    }

    String getToken() {
        return new String(token.getPassword());
    }

    /** Empties the field, e.g. while a stored token is being looked up. */
    void clearToken() {
        fillToken("");
        tokenHost = null;
    }

    /**
     * Shows a token read from the password safe for {@code forHost}. Ignored if the URL has been
     * edited since the read began: that token no longer belongs to what is on screen.
     */
    void setStoredToken(String value, String forHost) {
        if (!getHost().equals(forHost)) {
            return;
        }
        fillToken(value == null ? "" : value);
        tokenHost = value == null || value.isEmpty() ? null : forHost;
    }

    private void fillToken(String value) {
        settingToken = true;
        try {
            token.setText(value);
        } finally {
            settingToken = false;
        }
    }

    List<String> getExtraProjectPaths() {
        return new ArrayList<>(extraProjectPaths);
    }

    void setExtraProjectPaths(List<String> value) {
        extraProjectPaths.clear();
        if (value != null) {
            extraProjectPaths.addAll(new TreeSet<>(value));
        }
        refreshExtraProjectsModel();
    }
}
