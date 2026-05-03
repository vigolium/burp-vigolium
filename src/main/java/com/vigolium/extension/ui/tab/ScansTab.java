package com.vigolium.extension.ui.tab;

import com.vigolium.extension.model.Scan;
import com.vigolium.extension.model.ScanLogEntry;
import com.vigolium.extension.service.ScansResponse;
import com.vigolium.extension.ui.table.ColumnDef;
import com.vigolium.extension.ui.table.ScansColumnDefs;
import com.vigolium.extension.ui.table.ScansTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableColumn;

public class ScansTab extends JPanel {

    private final ScansTableModel tableModel;
    private final JTable table;

    private final JButton refreshBtn;
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JLabel pageInfoLabel;
    private final JComboBox<String> pageSizeCombo;

    private final JCheckBox autoRefreshCheck;
    private final Timer autoRefreshTimer;

    private final JComboBox<String> logLevelCombo;
    private final JComboBox<String> logPhaseCombo;
    private final JButton logRefreshBtn;
    private final JCheckBox logAutoScrollCheck;
    private final JTextArea logArea;

    private final JPopupMenu rowMenu;

    private Consumer<Scan> onScanSelected;
    private Consumer<Scan> onPauseScan;
    private Consumer<Scan> onResumeScan;
    private Consumer<Scan> onStopScan;
    private Consumer<Scan> onDeleteScan;
    private Runnable onLogFilterChanged;

    public ScansTab() {
        super(new BorderLayout());

        this.tableModel = new ScansTableModel();
        this.table = new JTable(tableModel);
        table.setName("scansTable");
        table.setAutoCreateRowSorter(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(
                new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        List<ColumnDef<Scan>> colDefs = ScansColumnDefs.create();
        for (int i = 0; i < colDefs.size() && i < table.getColumnCount(); i++) {
            ColumnDef<Scan> def = colDefs.get(i);
            TableColumn tc = table.getColumnModel().getColumn(i);
            if (def.width() > 0) {
                tc.setMinWidth(def.width());
                tc.setMaxWidth(def.width());
                tc.setPreferredWidth(def.width());
            } else if (def.preferredWidth() > 0) {
                tc.setPreferredWidth(def.preferredWidth());
            }
        }

        // Toolbar
        refreshBtn = new JButton("Refresh");
        refreshBtn.setName("scansRefreshButton");
        refreshBtn.putClientProperty("FlatLaf.styleClass", "primary");
        autoRefreshCheck = new JCheckBox("Auto-refresh (5s)");
        autoRefreshCheck.setName("scansAutoRefreshCheck");

        prevBtn = new JButton("Previous");
        prevBtn.setName("scansPrevButton");
        prevBtn.setEnabled(false);
        nextBtn = new JButton("Next");
        nextBtn.setName("scansNextButton");
        nextBtn.setEnabled(false);
        pageInfoLabel = new JLabel("No results");
        pageInfoLabel.setName("scansPageInfoLabel");
        pageSizeCombo = new JComboBox<>(new String[] {"20", "50", "100", "200", "500"});
        pageSizeCombo.setName("scansPageSizeCombo");
        pageSizeCombo.setSelectedItem("50");

        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbarLeft.add(refreshBtn);
        toolbarLeft.add(autoRefreshCheck);

        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        toolbarRight.add(prevBtn);
        toolbarRight.add(pageInfoLabel);
        toolbarRight.add(nextBtn);
        toolbarRight.add(new JLabel("Per page:"));
        toolbarRight.add(pageSizeCombo);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setName("scansToolbar");
        toolbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        toolbar.add(toolbarLeft, BorderLayout.WEST);
        toolbar.add(toolbarRight, BorderLayout.EAST);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().setUnitIncrement(25);
        tableScroll.getHorizontalScrollBar().setUnitIncrement(25);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        // Logs section (under the table)
        logLevelCombo = new JComboBox<>(new String[] {"Any level", "trace", "info", "warn", "error"});
        logLevelCombo.setName("scansLogLevelCombo");
        logPhaseCombo = new JComboBox<>(new String[] {
            "Any phase",
            "config",
            "heuristics",
            "harvest",
            "spidering",
            "discovery",
            "known-issue-scan",
            "audit",
            "sast",
            "seed"
        });
        logPhaseCombo.setName("scansLogPhaseCombo");
        logRefreshBtn = new JButton("Fetch Logs");
        logRefreshBtn.setName("scansLogRefreshButton");
        logAutoScrollCheck = new JCheckBox("Auto-scroll", true);
        logAutoScrollCheck.setName("scansLogAutoScrollCheck");

        JPanel logBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        logBar.add(new JLabel("Logs:"));
        logBar.add(new JLabel("Level:"));
        logBar.add(logLevelCombo);
        logBar.add(new JLabel("Phase:"));
        logBar.add(logPhaseCombo);
        logBar.add(logRefreshBtn);
        logBar.add(logAutoScrollCheck);

        logArea = new JTextArea();
        logArea.setName("scansLogArea");
        logArea.setEditable(false);
        logArea.putClientProperty("html.disable", Boolean.TRUE);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, getBaseFontSize()));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.getVerticalScrollBar().setUnitIncrement(25);
        logScroll.getHorizontalScrollBar().setUnitIncrement(25);
        logScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.setName("scansLogsPanel");
        JLabel logsHeader = new JLabel("Scan Logs");
        logsHeader.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        logsHeader.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        logsPanel.add(logsHeader, BorderLayout.NORTH);

        JPanel logInner = new JPanel(new BorderLayout());
        logInner.add(logBar, BorderLayout.NORTH);
        logInner.add(logScroll, BorderLayout.CENTER);
        logsPanel.add(logInner, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar, BorderLayout.NORTH);
        topPanel.add(tableScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, logsPanel);
        splitPane.setName("scansSplitPane");
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);

        add(splitPane, BorderLayout.CENTER);

        // Row context menu
        rowMenu = new JPopupMenu();
        rowMenu.setName("scansRowMenu");
        rowMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                rowMenu.removeAll();
                Scan selected = getSelectedScan();
                if (selected == null) return;

                JMenuItem fetchLogs = new JMenuItem("Fetch Logs");
                fetchLogs.addActionListener(ev -> triggerLogFetch());
                rowMenu.add(fetchLogs);

                if (selected.isRunning()) {
                    if (selected.isPaused()) {
                        JMenuItem resume = new JMenuItem("Resume");
                        resume.addActionListener(ev -> {
                            if (onResumeScan != null) onResumeScan.accept(selected);
                        });
                        rowMenu.add(resume);
                    } else {
                        JMenuItem pause = new JMenuItem("Pause");
                        pause.addActionListener(ev -> {
                            if (onPauseScan != null) onPauseScan.accept(selected);
                        });
                        rowMenu.add(pause);
                    }
                    JMenuItem stop = new JMenuItem("Stop");
                    stop.addActionListener(ev -> {
                        if (onStopScan != null) onStopScan.accept(selected);
                    });
                    rowMenu.add(stop);
                }
                rowMenu.addSeparator();
                JMenuItem del = new JMenuItem("Delete");
                del.addActionListener(ev -> {
                    if (onDeleteScan != null) onDeleteScan.accept(selected);
                });
                rowMenu.add(del);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        table.setComponentPopupMenu(rowMenu);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Scan selected = getSelectedScan();
                if (selected != null && onScanSelected != null) {
                    onScanSelected.accept(selected);
                }
            }
        });

        // Auto-refresh timer for scans table
        autoRefreshTimer = new Timer(5000, e -> refreshBtn.doClick());
        autoRefreshCheck.addActionListener(e -> {
            if (autoRefreshCheck.isSelected()) autoRefreshTimer.start();
            else autoRefreshTimer.stop();
        });

        // Log filter changed notifies controller
        ActionListener filterListener = e -> {
            if (onLogFilterChanged != null) onLogFilterChanged.run();
            triggerLogFetch();
        };
        logLevelCombo.addActionListener(filterListener);
        logPhaseCombo.addActionListener(filterListener);

        setName("scansSubTab");
    }

    private int getBaseFontSize() {
        return UIManager.getFont("defaultFont") != null
                ? UIManager.getFont("defaultFont").getSize()
                : 12;
    }

    private void triggerLogFetch() {
        logRefreshBtn.doClick();
    }

    public Scan getSelectedScan() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getRow(modelRow);
    }

    public void setLogs(List<ScanLogEntry> logs) {
        StringBuilder sb = new StringBuilder();
        for (ScanLogEntry entry : logs) {
            sb.append("[").append(entry.createdAt()).append("] ");
            if (entry.level() != null && !entry.level().isEmpty()) {
                sb.append(entry.level()).append(" ");
            }
            if (entry.phase() != null && !entry.phase().isEmpty()) {
                sb.append("[").append(entry.phase()).append("] ");
            }
            sb.append(entry.message()).append('\n');
        }
        logArea.setText(sb.toString());
        if (logAutoScrollCheck.isSelected()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    public void updatePagination(ScansResponse response) {
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

    public String getLogLevel() {
        String sel = (String) logLevelCombo.getSelectedItem();
        return "Any level".equals(sel) ? null : sel;
    }

    public String getLogPhase() {
        String sel = (String) logPhaseCombo.getSelectedItem();
        return "Any phase".equals(sel) ? null : sel;
    }

    public ScansTableModel getTableModel() {
        return tableModel;
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

    public void addLogFetchListener(ActionListener l) {
        logRefreshBtn.addActionListener(l);
    }

    public void setOnScanSelected(Consumer<Scan> listener) {
        this.onScanSelected = listener;
    }

    public void setOnPauseScan(Consumer<Scan> listener) {
        this.onPauseScan = listener;
    }

    public void setOnResumeScan(Consumer<Scan> listener) {
        this.onResumeScan = listener;
    }

    public void setOnStopScan(Consumer<Scan> listener) {
        this.onStopScan = listener;
    }

    public void setOnDeleteScan(Consumer<Scan> listener) {
        this.onDeleteScan = listener;
    }

    public void setOnLogFilterChanged(Runnable listener) {
        this.onLogFilterChanged = listener;
    }

    public void stopTimers() {
        if (autoRefreshTimer.isRunning()) autoRefreshTimer.stop();
    }
}
