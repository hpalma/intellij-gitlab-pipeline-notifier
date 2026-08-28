package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.hugopalma.gitlabpipelinenotifier.gitlab.GitLabClient;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabProject;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Lets the user pick which GitLab projects to watch from the ones their token can see, instead of
 * typing project paths by hand.
 *
 * <p>Checked state is tracked in {@link #checkedPaths} independently of what is currently listed,
 * so a path checked (or pre-checked from the existing settings) survives across searches even
 * though the list itself is rebuilt from scratch for every query.
 */
public class ProjectPickerDialog extends DialogWrapper {

    private static final int PER_PAGE = 100;
    /** Caps a single search at 500 results; a bigger match should be narrowed instead. */
    private static final int MAX_PAGES = 5;

    private final String host;
    private final String token;
    private final Set<String> checkedPaths;

    private final JBTextField search = new JBTextField();
    private final CheckBoxList<String> list = new CheckBoxList<>();
    private final JBLabel status = new JBLabel(" ");

    private final AtomicInteger requestSeq = new AtomicInteger();

    public ProjectPickerDialog(String host, String token, List<String> alreadyWatched) {
        super(true);
        this.host = host;
        this.token = token;
        this.checkedPaths = new TreeSet<>(alreadyWatched);

        list.setCheckBoxListListener((index, value) -> {
            String path = list.getItemAt(index);
            if (path == null) {
                return;
            }
            if (value) {
                checkedPaths.add(path);
            } else {
                checkedPaths.remove(path);
            }
        });

        setTitle("Select Projects to Watch");
        setOKButtonText("Add Selected");
        init();
        reload("");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JButton searchButton = new JButton("Search");
        ActionListener doSearch = _ -> reload(search.getText().trim());
        searchButton.addActionListener(doSearch);
        search.addActionListener(doSearch);

        JPanel searchRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);

        JBScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(480), JBUI.scale(320)));

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Search:", searchRow, 1, false)
                .addComponentFillVertically(scrollPane, JBUI.scale(4))
                .addComponent(status)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8));
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return search;
    }

    /** Fetches up to {@code MAX_PAGES * PER_PAGE} matching projects and repopulates the list. */
    private void reload(String searchTerm) {
        int myRequest = requestSeq.incrementAndGet();
        status.setText("Loading...");
        status.setForeground(UIUtil.getContextHelpForeground());

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<GitLabProject> finalProjects = new ArrayList<>();
            String error = null;
            try {
                GitLabClient client = new GitLabClient(host, token);
                for (int page = 1; page <= MAX_PAGES; page++) {
                    List<GitLabProject> pageResults = client.listProjects(searchTerm, PER_PAGE, page);
                    finalProjects.addAll(pageResults);
                    if (pageResults.size() < PER_PAGE) {
                        break;
                    }
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            SwingUtilities.invokeLater(() -> {
                // A newer search superseded this one; its own callback will populate the list.
                if (requestSeq.get() == myRequest) {
                    populate(finalProjects, finalError);
                }
            });
        });
    }

    private void populate(List<GitLabProject> projects, String error) {
        list.clear();
        if (error != null) {
            status.setText("Failed to load projects: " + error);
            status.setForeground(JBUI.CurrentTheme.Label.errorForeground());
            return;
        }

        Set<String> shown = new LinkedHashSet<>();
        for (GitLabProject project : projects) {
            String path = project.pathWithNamespace();
            if (shown.add(path)) {
                list.addItem(path, path, checkedPaths.contains(path));
            }
        }

        String count = shown.size() + (shown.size() == 1 ? " project" : " projects");
        status.setText(shown.size() == MAX_PAGES * PER_PAGE ? count + " (refine your search to see more)" : count);
        status.setForeground(UIUtil.getContextHelpForeground());
    }

    /** The union of what was already watched and what got checked here, in this session. */
    public List<String> getSelectedPaths() {
        return new ArrayList<>(checkedPaths);
    }
}
