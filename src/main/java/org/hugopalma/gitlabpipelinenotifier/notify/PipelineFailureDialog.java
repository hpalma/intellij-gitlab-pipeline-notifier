package org.hugopalma.gitlabpipelinenotifier.notify;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.UIManager;

/**
 * The loudest channel: a dialog brought to the front that has to be dismissed.
 *
 * <p>Failures accumulate into a single dialog rather than stacking one window per pipeline - a
 * broken shared branch can fail several pipelines within one poll, and N modal dialogs would be
 * hostile.
 */
public class PipelineFailureDialog extends DialogWrapper {

    private final List<PipelineFailure> failures = new ArrayList<>();
    private final JPanel content = new JPanel();

    public PipelineFailureDialog(@Nullable Project project, PipelineFailure initial) {
        super(project, false);
        failures.add(initial);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        setTitle("GitLab Pipeline Failed");
        setResizable(true);
        init();
    }

    /** Adds another failure to an already-visible dialog. Must be called on the EDT. */
    public void add(PipelineFailure failure) {
        for (PipelineFailure existing : failures) {
            if (existing.pipeline().id() == failure.pipeline().id()) {
                return;
            }
        }
        failures.add(failure);
        rebuild();
        content.revalidate();
        content.repaint();
        pack();
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
        rebuild();

        JBScrollPane scrollPane = new JBScrollPane(content);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(280)));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(JBUI.Borders.empty(10, 12));
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    private void rebuild() {
        content.removeAll();
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                content.add(new JSeparator());
            }
            content.add(renderFailure(failures.get(i)));
        }
    }

    private JPanel renderFailure(PipelineFailure failure) {
        JBLabel header = plainLabel(failure.title(), UIManager.getIcon("OptionPane.errorIcon"));
        header.setFont(header.getFont().deriveFont(Font.BOLD, header.getFont().getSize() + 2f));

        // The ActionLink constructors overload on ActionListener and AnAction, so an untyped
        // lambda is ambiguous; the cast picks the Swing one.
        ActionLink openLink = new ActionLink("Open in GitLab",
                (ActionListener) _ -> BrowserUtil.browse(failure.pipeline().webUrl()));

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addComponent(header)
                .addLabeledComponent("Branch:", plainLabel(nullToEmpty(failure.pipeline().ref())))
                .addLabeledComponent("Pipeline:",
                        plainLabel("#" + failure.pipeline().id() + " (" + failure.pipeline().shortSha() + ")"))
                .addLabeledComponent("Source:", plainLabel(nullToEmpty(failure.pipeline().source())));

        if (failure.triggeredBy() != null) {
            builder.addLabeledComponent("Triggered by:", plainLabel(failure.triggeredBy()));
        }
        if (!failure.failedJobs().isEmpty()) {
            builder.addLabeledComponent("Failed jobs:", plainLabel(String.join(", ", failure.failedJobs())));
        }

        JPanel panel = builder.addComponent(openLink).getPanel();
        panel.setBorder(JBUI.Borders.empty(6, 0));
        return panel;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Branch names, job names and other GitLab-sourced text are untrusted and can start with
     * "&lt;html&gt;", which Swing labels otherwise auto-render as markup. Disabling HTML
     * interpretation keeps every label's text literal regardless of content.
     */
    private static JBLabel plainLabel(String text) {
        return plainLabel(text, null);
    }

    private static JBLabel plainLabel(String text, javax.swing.Icon icon) {
        JBLabel label = icon == null ? new JBLabel(text) : new JBLabel(text, icon, JBLabel.LEADING);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "org.hugopalma.gitlabpipelinenotifier.FailureDialog";
    }
}
