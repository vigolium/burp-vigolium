package com.vigolium.extension.ui.tab;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

final class RefreshShortcut {

    static final KeyStroke KEYSTROKE =
            KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    private static final String ACTION_KEY = "vigolium.refreshRecords";

    private RefreshShortcut() {}

    static void install(JComponent view, JButton refreshButton) {
        view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KEYSTROKE, ACTION_KEY);
        view.getActionMap().put(ACTION_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (refreshButton.isEnabled()) refreshButton.doClick();
            }
        });
        refreshButton.setToolTipText("Refresh (Ctrl+Alt+R)");
    }
}
