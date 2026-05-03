package com.vigolium.extension.ui.tab;

import com.vigolium.extension.ui.panel.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class SettingsTab extends JPanel {

    private static final String DOCS_URL = "https://docs.vigolium.com/";

    private final ServerConnectionPanel serverConnectionPanel;
    private final ScanOptionsPanel scanOptionsPanel;
    private final ProxyModePanel proxyModePanel;
    private final RequestStatsPanel requestStatsPanel;
    private final FilterRulesPanel filterRulesPanel;
    private final HotkeysPanel hotkeysPanel;

    public SettingsTab(
            ServerConnectionPanel serverConnectionPanel,
            ScanOptionsPanel scanOptionsPanel,
            ProxyModePanel proxyModePanel,
            RequestStatsPanel requestStatsPanel,
            FilterRulesPanel filterRulesPanel,
            HotkeysPanel hotkeysPanel) {
        super(new BorderLayout());
        this.serverConnectionPanel = serverConnectionPanel;
        this.scanOptionsPanel = scanOptionsPanel;
        this.proxyModePanel = proxyModePanel;
        this.requestStatsPanel = requestStatsPanel;
        this.filterRulesPanel = filterRulesPanel;
        this.hotkeysPanel = hotkeysPanel;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(createDocsBanner());
        content.add(Box.createVerticalStrut(10));
        content.add(serverConnectionPanel);
        content.add(Box.createVerticalStrut(12));
        content.add(scanOptionsPanel);
        content.add(Box.createVerticalStrut(12));
        content.add(hotkeysPanel);
        content.add(Box.createVerticalStrut(12));
        content.add(proxyModePanel);
        content.add(Box.createVerticalStrut(12));
        content.add(requestStatsPanel);
        content.add(Box.createVerticalStrut(12));
        content.add(filterRulesPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);

        setName("settingsTab");
    }

    private JPanel createDocsBanner() {
        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inner.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel prefix = new JLabel("Need help? Check the documentation site at ");
        inner.add(prefix);

        JLabel link = new JLabel(DOCS_URL);
        link.setName("settingsDocsLink");
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Color linkColor = UIManager.getColor("Colors.palette.primary.3");
        if (linkColor == null) {
            linkColor = UIManager.getColor("Component.linkColor");
        }
        if (linkColor != null) {
            link.setForeground(linkColor);
        }
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            Map<TextAttribute, Object> attrs = new HashMap<>(base.getAttributes());
            attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            link.setFont(base.deriveFont(attrs));
        }
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDocs();
            }
        });
        inner.add(link);

        JLabel suffix = new JLabel(" if you have any questions.");
        inner.add(suffix);

        // Wrap in BorderLayout(WEST) so it always sits flush left in the BoxLayout column.
        // Keep default CENTER_ALIGNMENT so BoxLayout sizes the wrapper to the full container
        // width (matching the other panels) instead of offsetting it for mixed-alignment layout.
        JPanel banner = new JPanel(new BorderLayout());
        banner.setName("settingsDocsBanner");
        banner.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        banner.add(inner, BorderLayout.WEST);

        return banner;
    }

    private static void openDocs() {
        if (!Desktop.isDesktopSupported()) return;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return;
        try {
            desktop.browse(URI.create(DOCS_URL));
        } catch (Exception ignored) {
            // user-visible failure isn't worth a dialog here
        }
    }

    public ServerConnectionPanel getServerConnectionPanel() {
        return serverConnectionPanel;
    }

    public ScanOptionsPanel getScanOptionsPanel() {
        return scanOptionsPanel;
    }

    public ProxyModePanel getProxyModePanel() {
        return proxyModePanel;
    }

    public FilterRulesPanel getFilterRulesPanel() {
        return filterRulesPanel;
    }
}
