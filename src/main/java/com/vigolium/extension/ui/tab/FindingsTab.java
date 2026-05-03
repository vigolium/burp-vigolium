package com.vigolium.extension.ui.tab;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import com.vigolium.extension.service.FindingsResponse;
import com.vigolium.extension.ui.table.ColumnDef;
import com.vigolium.extension.ui.table.FindingsColumnDefs;
import com.vigolium.extension.ui.table.FindingsTableModel;
import com.vigolium.extension.ui.table.SeverityRenderer;
import com.vigolium.extension.ui.table.SortableHeaderRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

public class FindingsTab extends JPanel {

    private static final String EVIDENCE_PRIMARY = "Primary";

    private final FindingsTableModel tableModel;
    private final JTable table;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;

    private final JTextField searchField;
    private final JComboBox<String> severityCombo;
    private final JComboBox<String> moduleTypeCombo;
    private final JComboBox<String> findingSourceCombo;
    private final JTextField scanIdField;
    private final JTextField repoField;
    private final JTextField domainField;

    private final JComboBox<String> pageSizeCombo;
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JButton refreshBtn;
    private final JButton exportJsonBtn;
    private final JButton copyDetailsBtn;
    private final JLabel pageInfoLabel;

    private final JLabel summaryLabel;
    private final JTextArea metaArea;
    private final JComboBox<String> evidenceCombo;
    private final JButton toggleDescriptionBtn;
    private final JSplitPane detailSplit;
    private final JPanel summaryPanel;
    private int lastDescriptionDividerLocation = -1;
    private boolean descriptionVisible = false;

    private BiConsumer<String, String> onSortChanged;
    private Consumer<Finding> onFindingSelected;

    private String currentSortField;
    private String currentSortOrder;
    private int currentSortColumn = -1;

    private Finding currentFinding;
    private List<Finding.Evidence> evidences = new ArrayList<>();

    public FindingsTab(MontoyaApi api) {
        super(new BorderLayout());

        this.tableModel = new FindingsTableModel();
        this.table = new JTable(tableModel);
        table.setName("findingsTable");
        table.setAutoCreateRowSorter(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(
                new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));
        table.setDefaultRenderer(Severity.class, new SeverityRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        List<ColumnDef<Finding>> colDefs = FindingsColumnDefs.create();
        for (int i = 0; i < colDefs.size() && i < table.getColumnCount(); i++) {
            ColumnDef<Finding> def = colDefs.get(i);
            TableColumn tc = table.getColumnModel().getColumn(i);
            if (def.width() > 0) {
                tc.setMinWidth(def.width());
                tc.setMaxWidth(def.width());
                tc.setPreferredWidth(def.width());
            } else if (def.preferredWidth() > 0) {
                tc.setPreferredWidth(def.preferredWidth());
            }
        }

        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new SortableHeaderRenderer(
                header.getDefaultRenderer(), () -> currentSortColumn, () -> currentSortOrder));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col < 0) return;
                String colName = tableModel.getColumnName(col);
                String sortField = mapColumnToSortField(colName);
                if (sortField == null) return;

                if (sortField.equals(currentSortField)) {
                    if ("asc".equals(currentSortOrder)) {
                        currentSortOrder = "desc";
                        currentSortColumn = col;
                    } else if ("desc".equals(currentSortOrder)) {
                        currentSortField = null;
                        currentSortOrder = null;
                        currentSortColumn = -1;
                        header.repaint();
                        if (onSortChanged != null) onSortChanged.accept("found_at", "desc");
                        return;
                    } else {
                        currentSortOrder = "asc";
                        currentSortColumn = col;
                    }
                } else {
                    currentSortField = sortField;
                    currentSortOrder = "asc";
                    currentSortColumn = col;
                }
                header.repaint();
                if (onSortChanged != null) onSortChanged.accept(currentSortField, currentSortOrder);
            }
        });

        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        // ---- Detail pane ----
        summaryLabel = new JLabel(" ");
        summaryLabel.setName("findingsSummaryLabel");
        summaryLabel.putClientProperty("html.disable", Boolean.TRUE);
        summaryLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));

        metaArea = new JTextArea();
        metaArea.setName("findingsMetaArea");
        metaArea.putClientProperty("html.disable", Boolean.TRUE);
        metaArea.setEditable(false);
        metaArea.setLineWrap(true);
        metaArea.setWrapStyleWord(true);
        metaArea.setOpaque(false);
        metaArea.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));

        JScrollPane metaScroll = new JScrollPane(
                metaArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        metaScroll.setBorder(BorderFactory.createEmptyBorder());
        metaScroll.getVerticalScrollBar().setUnitIncrement(25);
        metaScroll.getHorizontalScrollBar().setUnitIncrement(25);

        summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setName("findingsSummaryPanel");
        summaryPanel.add(summaryLabel, BorderLayout.NORTH);
        summaryPanel.add(metaScroll, BorderLayout.CENTER);
        summaryPanel.setMinimumSize(new Dimension(0, 0));

        evidenceCombo = new JComboBox<>(new DefaultComboBoxModel<>(new String[] {EVIDENCE_PRIMARY}));
        evidenceCombo.setName("findingsEvidenceCombo");
        evidenceCombo.addActionListener(e -> updateEvidenceEditors());

        toggleDescriptionBtn = new JButton("Show Description");
        toggleDescriptionBtn.setName("findingsToggleDescriptionButton");
        toggleDescriptionBtn.addActionListener(e -> setDescriptionVisible(!descriptionVisible));

        JPanel evidenceBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        evidenceBar.setName("findingsEvidenceBar");
        evidenceBar.add(new JLabel("Evidence:"));
        evidenceBar.add(evidenceCombo);
        evidenceBar.add(toggleDescriptionBtn);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setName("findingsRequestPanel");
        JLabel requestLabel = new JLabel("Request");
        requestLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        requestLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        requestPanel.add(requestLabel, BorderLayout.NORTH);
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setName("findingsResponsePanel");
        JLabel responseLabel = new JLabel("Response");
        responseLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        responseLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        responsePanel.add(responseLabel, BorderLayout.NORTH);
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);

        JSplitPane editorSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        editorSplitPane.setName("findingsEditorSplitPane");
        editorSplitPane.setResizeWeight(0.5);
        editorSplitPane.setBorder(null);
        editorSplitPane.setDividerSize(5);

        JPanel evidencePane = new JPanel(new BorderLayout());
        evidencePane.setName("findingsEvidencePane");
        evidencePane.add(evidenceBar, BorderLayout.NORTH);
        evidencePane.add(editorSplitPane, BorderLayout.CENTER);
        evidencePane.setMinimumSize(new Dimension(0, 0));

        // Inner vertical split so the user can drag to expand the description/meta area.
        detailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, summaryPanel, evidencePane);
        detailSplit.setName("findingsDetailSplit");
        detailSplit.setResizeWeight(0.3);
        detailSplit.setBorder(null);
        // Description is hidden by default — collapse the top pane and its divider.
        summaryPanel.setVisible(false);
        detailSplit.setDividerSize(0);

        JPanel detailPane = new JPanel(new BorderLayout());
        detailPane.setName("findingsDetailPane");
        detailPane.add(detailSplit, BorderLayout.CENTER);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().setUnitIncrement(25);
        tableScroll.getHorizontalScrollBar().setUnitIncrement(25);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailPane);
        splitPane.setName("findingsSplitPane");
        splitPane.setResizeWeight(0.45);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);

        // ---- Toolbar: row 1 (search + severity + actions + pagination) ----
        searchField = new JTextField(20);
        searchField.setName("findingsSearchField");
        searchField.putClientProperty("JTextField.placeholderText", "Search findings...");

        severityCombo = new JComboBox<>(new String[] {"All", "Critical", "High", "Medium", "Low", "Info"});
        severityCombo.setName("findingsSeverityCombo");

        refreshBtn = new JButton("Refresh");
        refreshBtn.setName("findingsRefreshButton");
        refreshBtn.putClientProperty("FlatLaf.styleClass", "primary");
        exportJsonBtn = new JButton("Export JSON");
        exportJsonBtn.setName("findingsExportJsonButton");
        exportJsonBtn.setEnabled(false);
        copyDetailsBtn = new JButton("Copy Details");
        copyDetailsBtn.setName("findingsCopyDetailsButton");
        copyDetailsBtn.setEnabled(false);

        prevBtn = new JButton("Previous");
        prevBtn.setName("findingsPrevButton");
        prevBtn.setEnabled(false);
        nextBtn = new JButton("Next");
        nextBtn.setName("findingsNextButton");
        nextBtn.setEnabled(false);
        pageInfoLabel = new JLabel("No results");
        pageInfoLabel.setName("findingsPageInfoLabel");

        pageSizeCombo =
                new JComboBox<>(new String[] {"5", "10", "20", "50", "100", "200", "500", "1000", "5000", "10000"});
        pageSizeCombo.setName("findingsPageSizeCombo");
        pageSizeCombo.setSelectedItem("50");

        JPanel row1Left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1Left.add(new JLabel("Search:"));
        row1Left.add(searchField);
        row1Left.add(new JLabel("Severity:"));
        row1Left.add(severityCombo);
        row1Left.add(refreshBtn);
        row1Left.add(exportJsonBtn);
        row1Left.add(copyDetailsBtn);

        JPanel row1Right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        row1Right.add(prevBtn);
        row1Right.add(pageInfoLabel);
        row1Right.add(nextBtn);
        row1Right.add(new JLabel("Per page:"));
        row1Right.add(pageSizeCombo);

        JPanel row1 = new JPanel(new BorderLayout());
        row1.add(row1Left, BorderLayout.WEST);
        row1.add(row1Right, BorderLayout.EAST);

        // ---- Toolbar: row 2 (extended filters) ----
        moduleTypeCombo = new JComboBox<>(new String[] {"Any type", "active", "passive"});
        moduleTypeCombo.setName("findingsModuleTypeCombo");

        findingSourceCombo = new JComboBox<>(
                new String[] {"Any source", "audit", "spa", "agent", "oast", "source-tools", "extension", "archon"});
        findingSourceCombo.setName("findingsFindingSourceCombo");

        scanIdField = new JTextField(16);
        scanIdField.setName("findingsScanIdField");
        scanIdField.putClientProperty("JTextField.placeholderText", "Scan UUID");

        repoField = new JTextField(14);
        repoField.setName("findingsRepoField");
        repoField.putClientProperty("JTextField.placeholderText", "Repo name / URL");

        domainField = new JTextField(14);
        domainField.setName("findingsDomainField");
        domainField.putClientProperty("JTextField.placeholderText", "Domain (*.example.com)");

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row2.add(new JLabel("Type:"));
        row2.add(moduleTypeCombo);
        row2.add(new JLabel("Source:"));
        row2.add(findingSourceCombo);
        row2.add(new JLabel("Domain:"));
        row2.add(domainField);
        row2.add(new JLabel("Repo:"));
        row2.add(repoField);
        row2.add(new JLabel("Scan:"));
        row2.add(scanIdField);

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new javax.swing.BoxLayout(toolbar, javax.swing.BoxLayout.Y_AXIS));
        toolbar.setName("findingsToolbar");
        toolbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        toolbar.add(row1);
        toolbar.add(Box.createVerticalStrut(3));
        toolbar.add(row2);

        add(toolbar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    Finding finding = tableModel.getRow(modelRow);
                    if (onFindingSelected != null) {
                        onFindingSelected.accept(finding);
                    }
                }
            }
        });

        setName("findingsTab");
    }

    public void setOnFindingSelected(Consumer<Finding> listener) {
        this.onFindingSelected = listener;
    }

    /** Called by controller when a detailed finding is loaded. Updates summary, metadata, and evidence tabs. */
    public void showFinding(Finding finding) {
        this.currentFinding = finding;
        this.evidences = new ArrayList<>();

        if (finding == null) {
            summaryLabel.setText(" ");
            metaArea.setText("");
            evidenceCombo.setModel(new DefaultComboBoxModel<>(new String[] {EVIDENCE_PRIMARY}));
            setEditorBytes(null, null);
            copyDetailsBtn.setEnabled(false);
            return;
        }
        copyDetailsBtn.setEnabled(true);

        // Primary evidence from top-level request/response
        evidences.add(new Finding.Evidence(finding.request(), finding.response()));
        for (String raw : finding.additionalEvidence()) {
            evidences.add(Finding.parseEvidence(raw));
        }

        // Compact summary line (severity + module). Full description lives in metaArea below.
        String sev = finding.severity() != null ? finding.severity().label() : "";
        String module = finding.moduleName() != null ? finding.moduleName() : "";
        summaryLabel.setText(String.format("[%s] %s", sev, module));

        metaArea.setText(buildMetaText(finding));
        metaArea.setCaretPosition(0);

        // Evidence combo (Primary + Evidence #1..N)
        String[] labels = new String[evidences.size()];
        labels[0] = EVIDENCE_PRIMARY;
        for (int i = 1; i < evidences.size(); i++) {
            labels[i] = "Evidence #" + i;
        }
        evidenceCombo.setModel(new DefaultComboBoxModel<>(labels));
        evidenceCombo.setSelectedIndex(0);
        updateEvidenceEditors();
    }

    private void updateEvidenceEditors() {
        int idx = evidenceCombo.getSelectedIndex();
        if (idx < 0 || idx >= evidences.size()) {
            setEditorBytes(null, null);
            return;
        }
        Finding.Evidence ev = evidences.get(idx);
        byte[] reqBytes =
                ev.request() != null && !ev.request().isEmpty() ? ev.request().getBytes() : null;
        byte[] respBytes = ev.response() != null && !ev.response().isEmpty()
                ? ev.response().getBytes()
                : null;
        setEditorBytes(reqBytes, respBytes);
    }

    private void setEditorBytes(byte[] rawRequest, byte[] rawResponse) {
        HttpService service = deriveService();
        if (rawRequest != null && rawRequest.length > 0) {
            burp.api.montoya.core.ByteArray ba = burp.api.montoya.core.ByteArray.byteArray(rawRequest);
            HttpRequest req = service != null ? HttpRequest.httpRequest(service, ba) : HttpRequest.httpRequest(ba);
            requestEditor.setRequest(req);
        } else {
            requestEditor.setRequest(HttpRequest.httpRequest(""));
        }
        if (rawResponse != null && rawResponse.length > 0) {
            responseEditor.setResponse(
                    HttpResponse.httpResponse(burp.api.montoya.core.ByteArray.byteArray(rawResponse)));
        } else {
            responseEditor.setResponse(HttpResponse.httpResponse(""));
        }
    }

    /** Derive an HttpService from the first URL in matched_at so Repeater / Intruder can pick up the target. */
    private HttpService deriveService() {
        if (currentFinding == null
                || currentFinding.matchedAt() == null
                || currentFinding.matchedAt().isEmpty()) {
            return null;
        }
        String url = currentFinding.matchedAt().get(0);
        if (url == null || url.isEmpty()) return null;
        try {
            return HttpService.httpService(url);
        } catch (Exception e) {
            return null;
        }
    }

    /** Toggles the summary/description section of the detail pane. Remembers the prior divider location. */
    private void setDescriptionVisible(boolean visible) {
        if (visible == descriptionVisible) return;
        if (!visible) {
            lastDescriptionDividerLocation = detailSplit.getDividerLocation();
            summaryPanel.setVisible(false);
            detailSplit.setDividerSize(0);
            toggleDescriptionBtn.setText("Show Description");
        } else {
            summaryPanel.setVisible(true);
            detailSplit.setDividerSize(5);
            if (lastDescriptionDividerLocation > 0) {
                detailSplit.setDividerLocation(lastDescriptionDividerLocation);
            } else {
                detailSplit.setDividerLocation(detailSplit.getResizeWeight());
            }
            toggleDescriptionBtn.setText("Hide Description");
        }
        descriptionVisible = visible;
        detailSplit.revalidate();
        detailSplit.repaint();
    }

    private static String buildMetaText(Finding f) {
        StringBuilder sb = new StringBuilder();
        if (f.description() != null && !f.description().isEmpty()) {
            sb.append(f.description());
            if (!f.description().endsWith("\n")) sb.append("\n");
            sb.append("\n");
        }
        if (f.moduleShort() != null && !f.moduleShort().isEmpty()) {
            sb.append(f.moduleShort()).append("\n");
        }
        List<String> inline = new ArrayList<>();
        if (f.confidence() != null && !f.confidence().isEmpty()) inline.add("Confidence: " + f.confidence());
        if (f.findingSource() != null && !f.findingSource().isEmpty()) inline.add("Source: " + f.findingSource());
        if (f.moduleType() != null && !f.moduleType().isEmpty()) inline.add("Type: " + f.moduleType());
        if (!inline.isEmpty()) sb.append(String.join("   ", inline)).append("\n");

        if (f.repoName() != null && !f.repoName().isEmpty())
            sb.append("Repo: ").append(f.repoName()).append("\n");
        if (f.sourceFile() != null && !f.sourceFile().isEmpty())
            sb.append("File: ").append(f.sourceFile()).append("\n");
        if (f.scanUuid() != null && !f.scanUuid().isEmpty())
            sb.append("Scan: ").append(f.scanUuid()).append("\n");
        if (f.matchedAt() != null && !f.matchedAt().isEmpty())
            sb.append("Matched at: ").append(String.join(", ", f.matchedAt())).append("\n");
        if (f.tags() != null && !f.tags().isEmpty())
            sb.append("Tags: ").append(String.join(", ", f.tags())).append("\n");
        if (f.extractedResults() != null && !f.extractedResults().isEmpty())
            sb.append("Extracted: ")
                    .append(String.join(" | ", f.extractedResults()))
                    .append("\n");
        return sb.toString().stripTrailing();
    }

    public void updatePagination(FindingsResponse response) {
        int start = response.offset() + 1;
        int end = response.offset() + response.data().size();
        int total = response.total();
        if (total == 0) {
            pageInfoLabel.setText("No results");
        } else {
            pageInfoLabel.setText("Showing " + start + "-" + end + " of " + total);
        }
        boolean hasData = total > 0;
        exportJsonBtn.setEnabled(hasData);
        prevBtn.setEnabled(response.offset() > 0);
        nextBtn.setEnabled(response.hasMore());
    }

    private String mapColumnToSortField(String colName) {
        return switch (colName) {
            case "Severity" -> "severity";
            case "Module" -> "module_name";
            case "Confidence" -> "confidence";
            case "Found At" -> "found_at";
            default -> null;
        };
    }

    // ---- Getters for wiring in VigoliumExtension ----

    public FindingsTableModel getTableModel() {
        return tableModel;
    }

    public JTable getTable() {
        return table;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public JComboBox<String> getSeverityCombo() {
        return severityCombo;
    }

    public JComboBox<String> getModuleTypeCombo() {
        return moduleTypeCombo;
    }

    public JComboBox<String> getFindingSourceCombo() {
        return findingSourceCombo;
    }

    public JTextField getScanIdField() {
        return scanIdField;
    }

    public JTextField getRepoField() {
        return repoField;
    }

    public JTextField getDomainField() {
        return domainField;
    }

    public JComboBox<String> getPageSizeCombo() {
        return pageSizeCombo;
    }

    public void addRefreshListener(ActionListener l) {
        refreshBtn.addActionListener(l);
    }

    public void addPrevPageListener(ActionListener l) {
        prevBtn.addActionListener(l);
    }

    public void addNextPageListener(ActionListener l) {
        nextBtn.addActionListener(l);
    }

    public void addExportJsonListener(ActionListener l) {
        exportJsonBtn.addActionListener(l);
    }

    public void addCopyDetailsListener(ActionListener l) {
        copyDetailsBtn.addActionListener(l);
    }

    public void setOnSortChanged(BiConsumer<String, String> listener) {
        this.onSortChanged = listener;
    }

    public List<Finding> getFindings() {
        return tableModel.getRows();
    }

    public Finding getCurrentFinding() {
        return currentFinding;
    }
}
