package com.vigolium.extension.ui.tab;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.vigolium.extension.model.HttpRecord;
import com.vigolium.extension.service.HttpRecordsResponse;
import com.vigolium.extension.ui.table.ColumnDef;
import com.vigolium.extension.ui.table.HttpRecordsColumnDefs;
import com.vigolium.extension.ui.table.HttpRecordsTableModel;
import com.vigolium.extension.ui.table.SortableHeaderRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

public class HttpRecordsTab extends JPanel {

    private final HttpRecordsTableModel tableModel;
    private final JTable table;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;

    private final JTextField searchField;
    private final JTextField domainField;
    private final JComboBox<String> methodCombo;
    private final JComboBox<String> statusCombo;
    private final JTextField contentTypeField;
    private final JTextField sourceField;
    private final JTextField minRiskField;

    private final JComboBox<String> pageSizeCombo;
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JButton refreshBtn;
    private final JLabel pageInfoLabel;

    private final JPopupMenu rowMenu;

    private BiConsumer<String, String> onSortChanged;
    private Consumer<HttpRecord> onRecordSelected;
    private Consumer<HttpRecord> onScanRecord;
    private Consumer<HttpRecord> onSendToRepeater;
    private Consumer<HttpRecord> onDeleteRecord;

    private String currentSortField;
    private String currentSortOrder;
    private int currentSortColumn = -1;

    public HttpRecordsTab(MontoyaApi api) {
        super(new BorderLayout());

        this.tableModel = new HttpRecordsTableModel();
        this.table = new JTable(tableModel);
        table.setName("httpRecordsTable");
        table.setAutoCreateRowSorter(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(
                new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        List<ColumnDef<HttpRecord>> colDefs = HttpRecordsColumnDefs.create();
        for (int i = 0; i < colDefs.size() && i < table.getColumnCount(); i++) {
            ColumnDef<HttpRecord> def = colDefs.get(i);
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
                        if (onSortChanged != null) onSortChanged.accept("created_at", "desc");
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

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setName("httpRecordsRequestPanel");
        JLabel requestLabel = new JLabel("Request");
        requestLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        requestLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        requestPanel.add(requestLabel, BorderLayout.NORTH);
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setName("httpRecordsResponsePanel");
        JLabel responseLabel = new JLabel("Response");
        responseLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        responseLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        responsePanel.add(responseLabel, BorderLayout.NORTH);
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        editorSplit.setName("httpRecordsEditorSplit");
        editorSplit.setResizeWeight(0.5);
        editorSplit.setBorder(null);
        editorSplit.setDividerSize(5);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().setUnitIncrement(25);
        tableScroll.getHorizontalScrollBar().setUnitIncrement(25);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, editorSplit);
        splitPane.setName("httpRecordsSplitPane");
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);

        // Row 1: search + domain + refresh + pagination
        searchField = new JTextField(20);
        searchField.setName("httpRecordsSearchField");
        searchField.putClientProperty("JTextField.placeholderText", "Search URL/path...");

        domainField = new JTextField(16);
        domainField.setName("httpRecordsDomainField");
        domainField.putClientProperty("JTextField.placeholderText", "Domain (*.example.com)");

        refreshBtn = new JButton("Refresh");
        refreshBtn.setName("httpRecordsRefreshButton");
        refreshBtn.putClientProperty("FlatLaf.styleClass", "primary");

        prevBtn = new JButton("Previous");
        prevBtn.setName("httpRecordsPrevButton");
        prevBtn.setEnabled(false);
        nextBtn = new JButton("Next");
        nextBtn.setName("httpRecordsNextButton");
        nextBtn.setEnabled(false);
        pageInfoLabel = new JLabel("No results");
        pageInfoLabel.setName("httpRecordsPageInfoLabel");

        pageSizeCombo =
                new JComboBox<>(new String[] {"5", "10", "20", "50", "100", "200", "500", "1000", "5000", "10000"});
        pageSizeCombo.setName("httpRecordsPageSizeCombo");
        pageSizeCombo.setSelectedItem("50");

        JPanel row1Left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1Left.add(new JLabel("Search:"));
        row1Left.add(searchField);
        row1Left.add(new JLabel("Domain:"));
        row1Left.add(domainField);
        row1Left.add(refreshBtn);

        JPanel row1Right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        row1Right.add(prevBtn);
        row1Right.add(pageInfoLabel);
        row1Right.add(nextBtn);
        row1Right.add(new JLabel("Per page:"));
        row1Right.add(pageSizeCombo);

        JPanel row1 = new JPanel(new BorderLayout());
        row1.add(row1Left, BorderLayout.WEST);
        row1.add(row1Right, BorderLayout.EAST);

        // Row 2: method + status + content type + source + min risk
        methodCombo = new JComboBox<>(new String[] {"Any", "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"});
        methodCombo.setName("httpRecordsMethodCombo");
        statusCombo = new JComboBox<>(
                new String[] {"Any", "200", "201", "204", "301", "302", "400", "401", "403", "404", "500"});
        statusCombo.setName("httpRecordsStatusCombo");
        statusCombo.setEditable(true);
        contentTypeField = new JTextField(14);
        contentTypeField.setName("httpRecordsContentTypeField");
        contentTypeField.putClientProperty("JTextField.placeholderText", "Content-Type");
        sourceField = new JTextField(12);
        sourceField.setName("httpRecordsSourceField");
        sourceField.putClientProperty("JTextField.placeholderText", "Source");
        minRiskField = new JTextField(4);
        minRiskField.setName("httpRecordsMinRiskField");
        minRiskField.putClientProperty("JTextField.placeholderText", "0");

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row2.add(new JLabel("Method:"));
        row2.add(methodCombo);
        row2.add(new JLabel("Status:"));
        row2.add(statusCombo);
        row2.add(new JLabel("Content-Type:"));
        row2.add(contentTypeField);
        row2.add(new JLabel("Source:"));
        row2.add(sourceField);
        row2.add(new JLabel("Min risk:"));
        row2.add(minRiskField);

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setName("httpRecordsToolbar");
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
                    HttpRecord record = tableModel.getRow(modelRow);
                    if (onRecordSelected != null) {
                        onRecordSelected.accept(record);
                    }
                }
            }
        });

        // Row context menu
        rowMenu = new JPopupMenu();
        rowMenu.setName("httpRecordsRowMenu");
        rowMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                rowMenu.removeAll();
                HttpRecord selected = getSelectedRecord();
                if (selected == null) return;
                javax.swing.JMenuItem scan = new javax.swing.JMenuItem("Scan this record");
                scan.addActionListener(ev -> {
                    if (onScanRecord != null) onScanRecord.accept(selected);
                });
                javax.swing.JMenuItem repeater = new javax.swing.JMenuItem("Send to Repeater");
                repeater.addActionListener(ev -> {
                    if (onSendToRepeater != null) onSendToRepeater.accept(selected);
                });
                javax.swing.JMenuItem del = new javax.swing.JMenuItem("Delete");
                del.addActionListener(ev -> {
                    if (onDeleteRecord != null) onDeleteRecord.accept(selected);
                });
                rowMenu.add(scan);
                rowMenu.add(repeater);
                rowMenu.addSeparator();
                rowMenu.add(del);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        table.setComponentPopupMenu(rowMenu);

        setName("httpRecordsTab");
    }

    public HttpRecord getSelectedRecord() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getRow(modelRow);
    }

    public void setRecordDetail(HttpRecord record) {
        HttpService service = deriveService(record);
        byte[] rawRequest =
                record.rawRequest() != null && !record.rawRequest().isEmpty() ? record.rawRequestBytes() : null;
        byte[] rawResponse =
                record.rawResponse() != null && !record.rawResponse().isEmpty() ? record.rawResponseBytes() : null;

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

    private static HttpService deriveService(HttpRecord record) {
        if (record == null) return null;
        if (record.url() != null && !record.url().isEmpty()) {
            try {
                return HttpService.httpService(record.url());
            } catch (Exception ignored) {
                // fall through to hostname/port construction
            }
        }
        if (record.hostname() != null && !record.hostname().isEmpty()) {
            boolean secure = "https".equalsIgnoreCase(record.scheme());
            int port = record.port() > 0 ? record.port() : (secure ? 443 : 80);
            try {
                return HttpService.httpService(record.hostname(), port, secure);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public void updatePagination(HttpRecordsResponse response) {
        int start = response.offset() + 1;
        int end = response.offset() + response.data().size();
        int total = response.total();
        if (total == 0) {
            pageInfoLabel.setText("No results");
        } else {
            pageInfoLabel.setText("Showing " + start + "-" + end + " of " + total);
        }
        prevBtn.setEnabled(response.offset() > 0);
        nextBtn.setEnabled(response.hasMore());
    }

    private String mapColumnToSortField(String colName) {
        return switch (colName) {
            case "Method" -> "method";
            case "Status" -> "status_code";
            case "Path" -> "path";
            case "Time (ms)" -> "response_time";
            case "Sent At" -> "sent_at";
            default -> null;
        };
    }

    public HttpRecordsTableModel getTableModel() {
        return tableModel;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public JTextField getDomainField() {
        return domainField;
    }

    public JComboBox<String> getMethodCombo() {
        return methodCombo;
    }

    public JComboBox<String> getStatusCombo() {
        return statusCombo;
    }

    public JTextField getContentTypeField() {
        return contentTypeField;
    }

    public JTextField getSourceField() {
        return sourceField;
    }

    public JTextField getMinRiskField() {
        return minRiskField;
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

    public void setOnSortChanged(BiConsumer<String, String> listener) {
        this.onSortChanged = listener;
    }

    public void setOnRecordSelected(Consumer<HttpRecord> listener) {
        this.onRecordSelected = listener;
    }

    public void setOnScanRecord(Consumer<HttpRecord> listener) {
        this.onScanRecord = listener;
    }

    public void setOnSendToRepeater(Consumer<HttpRecord> listener) {
        this.onSendToRepeater = listener;
    }

    public void setOnDeleteRecord(Consumer<HttpRecord> listener) {
        this.onDeleteRecord = listener;
    }
}
