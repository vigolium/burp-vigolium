package com.vigolium.extension.ui.tab;

import com.vigolium.extension.model.AgentSession;
import com.vigolium.extension.service.AgentSessionsResponse;
import com.vigolium.extension.ui.table.AgentSessionsColumnDefs;
import com.vigolium.extension.ui.table.AgentSessionsTableModel;
import com.vigolium.extension.ui.table.ColumnDef;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;

public class AgentSessionsTab extends JPanel {

    private final AgentSessionsTableModel tableModel;
    private final JTable table;

    private final JComboBox<String> modeCombo;
    private final JButton refreshBtn;
    private final JCheckBox autoRefreshCheck;
    private final Timer autoRefreshTimer;

    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JLabel pageInfoLabel;
    private final JComboBox<String> pageSizeCombo;

    private final JButton logRefreshBtn;
    private final JCheckBox logAutoScrollCheck;
    private final JTextArea logArea;

    private Consumer<AgentSession> onSessionSelected;

    public AgentSessionsTab() {
        super(new BorderLayout());

        this.tableModel = new AgentSessionsTableModel();
        this.table = new JTable(tableModel);
        table.setName("agentSessionsTable");
        table.setAutoCreateRowSorter(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(
                new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        RecordToolbarStyle.styleTable(table);

        List<ColumnDef<AgentSession>> colDefs = AgentSessionsColumnDefs.create();
        for (int i = 0; i < colDefs.size() && i < table.getColumnCount(); i++) {
            ColumnDef<AgentSession> def = colDefs.get(i);
            TableColumn tc = table.getColumnModel().getColumn(i);
            if (def.width() > 0) {
                tc.setMinWidth(def.width());
                tc.setMaxWidth(def.width());
                tc.setPreferredWidth(def.width());
            } else if (def.preferredWidth() > 0) {
                tc.setPreferredWidth(def.preferredWidth());
            }
        }

        modeCombo = new JComboBox<>(new String[] {"Any mode", "query", "autopilot", "swarm"});
        modeCombo.setName("agentSessionsModeCombo");

        refreshBtn = new JButton("Refresh");
        refreshBtn.setName("agentSessionsRefreshButton");
        refreshBtn.putClientProperty("FlatLaf.styleClass", "primary");

        autoRefreshCheck = new JCheckBox("Auto-refresh (5s)");
        autoRefreshCheck.setName("agentSessionsAutoRefreshCheck");

        prevBtn = new JButton("Previous");
        prevBtn.setName("agentSessionsPrevButton");
        prevBtn.setEnabled(false);
        nextBtn = new JButton("Next");
        nextBtn.setName("agentSessionsNextButton");
        nextBtn.setEnabled(false);
        pageInfoLabel = new JLabel("No results");
        pageInfoLabel.setName("agentSessionsPageInfoLabel");
        pageSizeCombo = new JComboBox<>(new String[] {"20", "50", "100", "200", "500"});
        pageSizeCombo.setName("agentSessionsPageSizeCombo");
        pageSizeCombo.setSelectedItem("50");

        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, RecordToolbarStyle.CONTROL_GAP, 0));
        toolbarLeft.setName("agentSessionsPrimaryControls");
        toolbarLeft.add(refreshBtn);
        toolbarLeft.add(RecordToolbarStyle.separator());
        toolbarLeft.add(new JLabel("Mode:"));
        toolbarLeft.add(modeCombo);
        toolbarLeft.add(autoRefreshCheck);

        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, RecordToolbarStyle.CONTROL_GAP, 0));
        toolbarRight.add(pageInfoLabel);
        toolbarRight.add(prevBtn);
        toolbarRight.add(nextBtn);
        toolbarRight.add(RecordToolbarStyle.separator());
        toolbarRight.add(new JLabel("Rows:"));
        toolbarRight.add(pageSizeCombo);

        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setName("agentSessionsToolbar");
        toolbar.setBorder(RecordToolbarStyle.toolbarBorder());
        toolbar.add(toolbarLeft, BorderLayout.WEST);
        toolbar.add(toolbarRight, BorderLayout.EAST);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().setUnitIncrement(25);
        tableScroll.getHorizontalScrollBar().setUnitIncrement(25);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar, BorderLayout.NORTH);
        topPanel.add(tableScroll, BorderLayout.CENTER);

        // Logs section (runtime.log dump)
        logRefreshBtn = new JButton("Fetch runtime.log");
        logRefreshBtn.setName("agentSessionsLogRefreshButton");
        logAutoScrollCheck = new JCheckBox("Auto-scroll", true);
        logAutoScrollCheck.setName("agentSessionsLogAutoScrollCheck");

        JPanel logBar = new JPanel(new FlowLayout(FlowLayout.LEFT, RecordToolbarStyle.CONTROL_GAP, 0));
        logBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        logBar.add(logRefreshBtn);
        logBar.add(RecordToolbarStyle.separator());
        logBar.add(logAutoScrollCheck);

        logArea = new JTextArea();
        logArea.setName("agentSessionsLogArea");
        logArea.setEditable(false);
        logArea.putClientProperty("html.disable", Boolean.TRUE);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, getBaseFontSize()));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.getVerticalScrollBar().setUnitIncrement(25);
        logScroll.getHorizontalScrollBar().setUnitIncrement(25);
        logScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.setName("agentSessionsLogsPanel");
        JLabel logsHeader = new JLabel("Agent Session Log");
        RecordToolbarStyle.makeBold(logsHeader);
        logsHeader.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        logsPanel.add(logsHeader, BorderLayout.NORTH);

        JPanel logInner = new JPanel(new BorderLayout());
        logInner.add(logBar, BorderLayout.NORTH);
        logInner.add(logScroll, BorderLayout.CENTER);
        logsPanel.add(logInner, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, logsPanel);
        splitPane.setName("agentSessionsSplitPane");
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);

        add(splitPane, BorderLayout.CENTER);
        RefreshShortcut.install(this, refreshBtn);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                AgentSession selected = getSelectedSession();
                if (selected != null && onSessionSelected != null) {
                    onSessionSelected.accept(selected);
                }
            }
        });

        autoRefreshTimer = new Timer(5000, e -> refreshBtn.doClick());
        autoRefreshCheck.addActionListener(e -> {
            if (autoRefreshCheck.isSelected()) autoRefreshTimer.start();
            else autoRefreshTimer.stop();
        });

        setName("agentSessionsSubTab");
    }

    private int getBaseFontSize() {
        return UIManager.getFont("defaultFont") != null
                ? UIManager.getFont("defaultFont").getSize()
                : 12;
    }

    public AgentSession getSelectedSession() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getRow(modelRow);
    }

    public void setLogText(String text) {
        logArea.setText(text != null ? text : "");
        if (logAutoScrollCheck.isSelected()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    public void updatePagination(AgentSessionsResponse response) {
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

    public String getSelectedMode() {
        String sel = (String) modeCombo.getSelectedItem();
        return "Any mode".equals(sel) ? null : sel;
    }

    public AgentSessionsTableModel getTableModel() {
        return tableModel;
    }

    public JComboBox<String> getModeCombo() {
        return modeCombo;
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

    public void setOnSessionSelected(Consumer<AgentSession> listener) {
        this.onSessionSelected = listener;
    }

    public void stopTimers() {
        if (autoRefreshTimer.isRunning()) autoRefreshTimer.stop();
    }
}
