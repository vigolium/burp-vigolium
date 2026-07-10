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
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

public class SettingsTab extends JPanel {

    private static final String DOCS_URL = "https://docs.vigolium.com/";

    private final ServerConnectionPanel serverConnectionPanel;
    private final ScanOptionsPanel scanOptionsPanel;
    private final RequestStatsPanel requestStatsPanel;
    private final HotkeysPanel hotkeysPanel;

    public SettingsTab(
            ServerConnectionPanel serverConnectionPanel,
            ScanOptionsPanel scanOptionsPanel,
            RequestStatsPanel requestStatsPanel,
            HotkeysPanel hotkeysPanel) {
        this(serverConnectionPanel, scanOptionsPanel, requestStatsPanel, hotkeysPanel, resolveExtensionVersion());
    }

    SettingsTab(
            ServerConnectionPanel serverConnectionPanel,
            ScanOptionsPanel scanOptionsPanel,
            RequestStatsPanel requestStatsPanel,
            HotkeysPanel hotkeysPanel,
            String extensionVersion) {
        super(new BorderLayout());
        this.serverConnectionPanel = serverConnectionPanel;
        this.scanOptionsPanel = scanOptionsPanel;
        this.requestStatsPanel = requestStatsPanel;
        this.hotkeysPanel = hotkeysPanel;

        ScrollablePanel content = new ScrollablePanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(0, 0, 16, 0);
        content.add(createOverview(extensionVersion), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        content.add(createCard(serverConnectionPanel, "serverConnectionCard"), gbc);

        gbc.gridy++;
        content.add(createCard(scanOptionsPanel, "scanOptionsCard"), gbc);

        gbc.gridy++;
        content.add(createCard(hotkeysPanel, "keyboardShortcutsCard"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(createCard(requestStatsPanel, "requestStatisticsCard"), gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        content.add(new JPanel(), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);

        setName("settingsTab");
    }

    private JPanel createOverview(String extensionVersion) {
        JPanel overview = new JPanel(new BorderLayout(0, 8));
        overview.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        overview.setName("settingsOverview");

        JPanel heading = new JPanel(new BorderLayout(0, 4));
        JPanel titleRow = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Vigolium Settings");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize() + 6f));
        titleRow.add(title, BorderLayout.WEST);

        String version = extensionVersion == null || extensionVersion.isBlank() ? "development" : extensionVersion;
        JLabel versionLabel = new JLabel("Extension v" + version);
        versionLabel.setName("settingsExtensionVersionLabel");
        versionLabel.putClientProperty("FlatLaf.styleClass", "small");
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) versionLabel.setForeground(muted);
        titleRow.add(versionLabel, BorderLayout.EAST);

        heading.add(titleRow, BorderLayout.NORTH);
        heading.add(
                new JLabel("Configure the server connection, scanning defaults, shortcuts, and activity counters."),
                BorderLayout.CENTER);
        overview.add(heading, BorderLayout.NORTH);
        overview.add(createDocsBanner(), BorderLayout.CENTER);
        return overview;
    }

    private static JPanel createCard(Component content, String name) {
        JPanel card = new JPanel(new BorderLayout());
        card.setName(name);
        card.setBorder(new CompoundBorder(createCardOutline(), BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private static Border createCardOutline() {
        Color borderColor = UIManager.getColor("Component.borderColor");
        if (borderColor == null) borderColor = UIManager.getColor("Separator.foreground");
        if (borderColor == null) borderColor = Color.GRAY;
        return BorderFactory.createLineBorder(borderColor);
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

        JPanel banner = new JPanel(new BorderLayout());
        banner.setName("settingsDocsBanner");
        banner.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        banner.add(inner, BorderLayout.WEST);

        return banner;
    }

    private static String resolveExtensionVersion() {
        String version = SettingsTab.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
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

    private static final class ScrollablePanel extends JPanel implements Scrollable {
        private ScrollablePanel(GridBagLayout layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 25;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(
                    25, orientation == SwingConstants.VERTICAL ? visibleRect.height - 25 : visibleRect.width - 25);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
