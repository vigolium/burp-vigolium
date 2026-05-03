package com.vigolium.extension.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public class HotkeyDialog extends JDialog {

    private final String name;
    private final String originalMontoyaKey;
    private final Consumer<String> onApply;

    private KeyStroke currentStroke;
    private JTextField keystrokeField;
    private JButton applyButton;
    private KeyEventDispatcher dispatcher;

    public HotkeyDialog(Window owner, String name, String currentMontoyaKey, Consumer<String> onApply) {
        super(owner, currentMontoyaKey == null ? "Add hotkey" : "Edit hotkey", ModalityType.APPLICATION_MODAL);
        this.name = name;
        this.originalMontoyaKey = currentMontoyaKey;
        this.onApply = onApply;
        this.currentStroke = parseMontoyaKey(currentMontoyaKey);

        setName("hotkeyDialog");
        buildUI();
        installKeyCapture();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
            }
        });

        setPreferredSize(new Dimension(600, 200));
        pack();
        setLocationRelativeTo(owner);
    }

    public void open() {
        setVisible(true);
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagLayout layout = (GridBagLayout) centerPanel.getLayout();
        layout.columnWidths = new int[] {0};
        layout.rowHeights = new int[] {0, 5, 0};

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 1.0;

        JLabel descLabel = new JLabel("Enter a hotkey for \"" + name + "\"");
        gbc.gridy = 0;
        centerPanel.add(descLabel, gbc);

        keystrokeField = new JTextField(formatKeystroke(currentStroke));
        keystrokeField.setEditable(false);
        keystrokeField.setColumns(15);
        keystrokeField.setFocusable(true);
        keystrokeField.setName("hotkeyKeystrokeField");
        gbc.gridy = 2;
        centerPanel.add(keystrokeField, gbc);

        content.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(30, 0, 30, 20));
        GridBagLayout btnLayout = (GridBagLayout) buttonPanel.getLayout();
        btnLayout.columnWidths = new int[] {0, 10, 0, 10, 0, 10, 0};
        btnLayout.rowHeights = new int[] {0};

        gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 1.0;
        buttonPanel.add(Box.createHorizontalGlue(), gbc);

        JButton removeButton = new JButton("Remove");
        removeButton.setName("hotkeyRemoveButton");
        removeButton.setVisible(originalMontoyaKey != null);
        removeButton.addActionListener(e -> {
            currentStroke = null;
            apply();
        });
        gbc.gridx = 2;
        gbc.weightx = 0;
        buttonPanel.add(removeButton, gbc);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setName("hotkeyCancelButton");
        cancelButton.addActionListener(e -> cancel());
        gbc.gridx = 4;
        buttonPanel.add(cancelButton, gbc);

        applyButton = new JButton("Apply");
        applyButton.setName("hotkeyApplyButton");
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> apply());
        gbc.gridx = 6;
        buttonPanel.add(applyButton, gbc);

        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private void installKeyCapture() {
        dispatcher = event -> {
            if (!isVisible()) return false;

            if (isModifierKeyPress(event)) {
                KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiersEx());
                onKeystrokeCaptured(stroke);
                event.consume();
                return true;
            }

            if (isClearKey(event)) {
                onKeystrokeCaptured(null);
                event.consume();
                return true;
            }

            if (isEnterKey(event)) {
                if (applyButton.isEnabled()) apply();
                return true;
            }

            if (isEscapeKey(event)) {
                cancel();
                return true;
            }

            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
    }

    private void onKeystrokeCaptured(KeyStroke stroke) {
        currentStroke = stroke;
        keystrokeField.setText(formatKeystroke(stroke));
        updateApplyState();
    }

    private void updateApplyState() {
        String newKey = toMontoyaKey(currentStroke);
        boolean changed = (newKey == null && originalMontoyaKey != null)
                || (newKey != null && !newKey.equals(originalMontoyaKey));
        applyButton.setEnabled(changed);
    }

    private void apply() {
        cleanup();
        String key = toMontoyaKey(currentStroke);
        dispose();
        onApply.accept(key);
    }

    private void cancel() {
        cleanup();
        dispose();
    }

    private void cleanup() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
    }

    private static boolean isModifierKeyPress(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) return false;
        int keyCode = e.getKeyCode();
        return e.getModifiersEx() != 0
                && keyCode != KeyEvent.VK_SHIFT
                && keyCode != KeyEvent.VK_CONTROL
                && keyCode != KeyEvent.VK_ALT
                && keyCode != KeyEvent.VK_META;
    }

    private static boolean isClearKey(KeyEvent e) {
        return e.getID() == KeyEvent.KEY_PRESSED
                && (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE);
    }

    private static boolean isEnterKey(KeyEvent e) {
        return e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER;
    }

    private static boolean isEscapeKey(KeyEvent e) {
        return e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE;
    }

    public static String formatKeystroke(KeyStroke stroke) {
        if (stroke == null) return "";
        StringBuilder sb = new StringBuilder();
        int mod = stroke.getModifiers();
        if ((mod & KeyEvent.CTRL_DOWN_MASK) != 0 || (mod & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((mod & KeyEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
        if ((mod & KeyEvent.ALT_DOWN_MASK) != 0) sb.append("Alt+");
        sb.append(keyName(stroke.getKeyCode()));
        return sb.toString();
    }

    public static String toMontoyaKey(KeyStroke stroke) {
        return stroke == null ? null : formatKeystroke(stroke);
    }

    public static KeyStroke parseMontoyaKey(String montoyaKey) {
        if (montoyaKey == null || montoyaKey.isBlank()) return null;
        String[] parts = montoyaKey.trim().split("\\+");
        int modifiers = 0;
        String keyName = parts[parts.length - 1];
        int platformCtrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        for (int i = 0; i < parts.length - 1; i++) {
            switch (parts[i]) {
                case "Ctrl" -> modifiers |= platformCtrl;
                case "Shift" -> modifiers |= KeyEvent.SHIFT_DOWN_MASK;
                case "Alt" -> modifiers |= KeyEvent.ALT_DOWN_MASK;
            }
        }
        int keyCode = keyCodeFromName(keyName);
        return keyCode != -1 ? KeyStroke.getKeyStroke(keyCode, modifiers) : null;
    }

    private static String keyName(int keyCode) {
        if ((keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
                || (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)) {
            return String.valueOf((char) keyCode);
        }
        return KeyEvent.getKeyText(keyCode);
    }

    private static final java.util.Map<String, Integer> KEY_NAME_TO_CODE = buildKeyNameMap();

    private static java.util.Map<String, Integer> buildKeyNameMap() {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (int vk = 0; vk < 0xFFFF; vk++) {
            try {
                String text = KeyEvent.getKeyText(vk);
                if (!text.startsWith("Unknown")) {
                    map.putIfAbsent(text, vk);
                }
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static int keyCodeFromName(String name) {
        if (name.length() == 1) {
            char c = name.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return c;
        }
        return KEY_NAME_TO_CODE.getOrDefault(name, -1);
    }
}
