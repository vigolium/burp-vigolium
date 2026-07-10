package com.vigolium.extension.ui.tab;

import com.vigolium.extension.ui.panel.BridgePanel;
import com.vigolium.extension.ui.panel.FilterRulesPanel;
import com.vigolium.extension.ui.panel.ProxyModePanel;
import com.vigolium.extension.ui.panel.SiteMapSnapshotPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

public class BridgeTab extends JPanel {

    private final SiteMapSnapshotPanel siteMapSnapshotPanel;
    private final BridgePanel bridgePanel;
    private final ProxyModePanel proxyModePanel;
    private final FilterRulesPanel filterRulesPanel;

    public BridgeTab(
            SiteMapSnapshotPanel siteMapSnapshotPanel,
            BridgePanel bridgePanel,
            ProxyModePanel proxyModePanel,
            FilterRulesPanel filterRulesPanel) {
        super(new BorderLayout());
        this.siteMapSnapshotPanel = siteMapSnapshotPanel;
        this.bridgePanel = bridgePanel;
        this.proxyModePanel = proxyModePanel;
        this.filterRulesPanel = filterRulesPanel;

        ScrollablePanel content = new ScrollablePanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(0, 0, 16, 0);
        content.add(createOverview(), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        content.add(createCard(siteMapSnapshotPanel, "siteMapSnapshotCard"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        content.add(createCard(bridgePanel, "liveBridgeCard"), gbc);

        gbc.gridy++;
        content.add(createCard(proxyModePanel, "proxyInterceptionCard"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(createCard(filterRulesPanel, "proxyFilterRulesCard"), gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        content.add(new JPanel(), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        setName("bridgeTab");
    }

    private static JPanel createOverview() {
        JPanel overview = new JPanel(new BorderLayout(0, 4));
        overview.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        overview.setName("bridgeOverview");

        JLabel title = new JLabel("Burp Integration Bridge");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize() + 6f));
        overview.add(title, BorderLayout.NORTH);

        JLabel description = new JLabel(
                "Synchronize traffic, expose a loopback-only read view, or control automatic Proxy forwarding.");
        overview.add(description, BorderLayout.CENTER);
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

    public SiteMapSnapshotPanel getSiteMapSnapshotPanel() {
        return siteMapSnapshotPanel;
    }

    public BridgePanel getBridgePanel() {
        return bridgePanel;
    }

    public ProxyModePanel getProxyModePanel() {
        return proxyModePanel;
    }

    public FilterRulesPanel getFilterRulesPanel() {
        return filterRulesPanel;
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
