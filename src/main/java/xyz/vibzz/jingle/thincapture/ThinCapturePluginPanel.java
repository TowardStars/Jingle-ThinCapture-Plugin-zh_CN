package xyz.vibzz.jingle.thincapture;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ThinCapturePluginPanel {
    public JPanel mainPanel;
    private JPanel capturesContainer;
    private final List<JPanel> capturePanels = new ArrayList<>();

    private JTextField thinWField, thinHField, fpsField;

    public ThinCapturePluginPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        ThinCaptureOptions o = ThinCapture.getOptions();

        // ===== General Settings =====
        JPanel generalPanel = new JPanel();
        generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
        generalPanel.setBorder(BorderFactory.createTitledBorder("General"));
        generalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel thinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        thinRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        thinRow.add(new JLabel("Thin BT size:"));
        thinWField = new JTextField(String.valueOf(o.thinBTWidth), 4);
        thinHField = new JTextField(String.valueOf(o.thinBTHeight), 4);
        thinRow.add(thinWField);
        thinRow.add(new JLabel("\u00d7"));
        thinRow.add(thinHField);
        JButton thinApply = new JButton("Apply");
        thinApply.addActionListener(a -> {
            o.thinBTWidth = intFrom(thinWField, 280);
            o.thinBTHeight = intFrom(thinHField, 1000);
            thinWField.setText(String.valueOf(o.thinBTWidth));
            thinHField.setText(String.valueOf(o.thinBTHeight));
        });
        thinRow.add(thinApply);
        JLabel desc = new JLabel("Must match your Thin BT size in the Resizing script.");
        desc.setFont(desc.getFont().deriveFont(Font.ITALIC, 11f));
        thinRow.add(desc);
        generalPanel.add(thinRow);

        JPanel fpsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fpsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        fpsRow.add(new JLabel("FPS limit:"));
        fpsField = new JTextField(String.valueOf(o.fpsLimit), 4);
        fpsField.getDocument().addDocumentListener(docListener(() -> {
            o.fpsLimit = clamp(intFrom(fpsField, 30), 5, 240);
            ThinCapture.updateFpsLimit();
        }));
        fpsRow.add(fpsField);
        generalPanel.add(fpsRow);

        mainPanel.add(generalPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // ===== Captures Container =====
        capturesContainer = new JPanel();
        capturesContainer.setLayout(new BoxLayout(capturesContainer, BoxLayout.Y_AXIS));
        capturesContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Build panels for existing captures
        for (int i = 0; i < o.captures.size(); i++) {
            JPanel panel = buildCapturePanel(i);
            capturePanels.add(panel);
            capturesContainer.add(panel);
            capturesContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        mainPanel.add(capturesContainer);

        // Add Capture button
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton addBtn = new JButton("+ Add Capture");
        addBtn.addActionListener(a -> {
            String name = JOptionPane.showInputDialog(mainPanel, "Capture name:", "New Capture");
            if (name != null && !name.trim().isEmpty()) {
                ThinCapture.addCapture(name.trim());
                rebuildCaptures();
            }
        });
        addRow.add(addBtn);
        mainPanel.add(addRow);

        mainPanel.add(Box.createVerticalGlue());
    }

    private JPanel buildCapturePanel(int index) {
        ThinCaptureOptions o = ThinCapture.getOptions();
        CaptureConfig c = o.captures.get(index);

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(c.name));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Top row: enabled + rename + remove + apply
        JCheckBox enableBox = new JCheckBox("Enabled");
        enableBox.setSelected(c.enabled);
        enableBox.addActionListener(a -> c.enabled = enableBox.isSelected());

        JButton renameBtn = new JButton("Rename");
        renameBtn.setMargin(new Insets(1, 6, 1, 6));
        renameBtn.addActionListener(a -> {
            String newName = JOptionPane.showInputDialog(mainPanel, "New name:", c.name);
            if (newName != null && !newName.trim().isEmpty()) {
                ThinCapture.renameCapture(index, newName.trim());
                rebuildCaptures();
            }
        });

        JButton removeBtn = new JButton("Remove");
        removeBtn.setMargin(new Insets(1, 6, 1, 6));
        removeBtn.setForeground(Color.RED);
        removeBtn.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "Remove capture \"" + c.name + "\"?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                ThinCapture.removeCapture(index);
                rebuildCaptures();
            }
        });

        JButton applyBtn = new JButton("Apply");
        applyBtn.setMargin(new Insets(1, 6, 1, 6));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        topRow.add(enableBox);
        topRow.add(renameBtn);
        topRow.add(removeBtn);
        section.add(topRow);

        // Monitor position
        JTextField ox = field(c.screenX), oy = field(c.screenY), ow = field(c.screenW), oh = field(c.screenH);
        JPanel overlayRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        overlayRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        overlayRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        overlayRow.add(new JLabel("Monitor  Starting X:"));overlayRow.add(ox);
        overlayRow.add(new JLabel("Starting Y:"));overlayRow.add(oy);
        overlayRow.add(new JLabel("Window Width:"));overlayRow.add(ow);
        overlayRow.add(new JLabel("Window Height:"));overlayRow.add(oh);
        JButton selectMonitor = new JButton("Select");
        selectMonitor.setMargin(new Insets(1, 6, 1, 6));
        selectMonitor.addActionListener(a -> RegionSelector.selectOnScreen(r -> {
            ox.setText(String.valueOf(r.x));
            oy.setText(String.valueOf(r.y));
            ow.setText(String.valueOf(r.width));
            oh.setText(String.valueOf(r.height));
        }));
        overlayRow.add(selectMonitor);
        section.add(overlayRow);

        // MC region
        JTextField rx = field(c.captureX), ry = field(c.captureY), rw = field(c.captureW), rh = field(c.captureH);
        JPanel regionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        regionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        regionRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        regionRow.add(new JLabel("MC Region Starting X:"));regionRow.add(rx);
        regionRow.add(new JLabel("Starting Y:"));regionRow.add(ry);
        regionRow.add(new JLabel("Capture Width:"));regionRow.add(rw);
        regionRow.add(new JLabel("Capture Height:"));regionRow.add(rh);
        JButton selectMC = new JButton("Select");
        selectMC.setMargin(new Insets(1, 6, 1, 6));
        selectMC.addActionListener(a -> RegionSelector.selectOnMCWindow(r -> {
            rx.setText(String.valueOf(r.x));
            ry.setText(String.valueOf(r.y));
            rw.setText(String.valueOf(r.width));
            rh.setText(String.valueOf(r.height));
        }));
        regionRow.add(selectMC);
        section.add(regionRow);

        // Filtering
        JCheckBox textOnlyBox = new JCheckBox("Text only (keep bright pixels)");
        textOnlyBox.setSelected(c.textOnly);
        textOnlyBox.addActionListener(a -> c.textOnly = textOnlyBox.isSelected());

        JTextField threshField = new JTextField(String.valueOf(c.textThreshold), 3);
        threshField.getDocument().addDocumentListener(docListener(() ->
                c.textThreshold = clamp(intFrom(threshField, 200), 0, 255)
        ));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        filterRow.add(textOnlyBox);
        filterRow.add(new JLabel("Threshold:"));
        filterRow.add(threshField);
        section.add(filterRow);

        // Background
        JCheckBox transBgBox = new JCheckBox("Transparent background");
        transBgBox.setSelected(c.transparentBg);
        JTextField bgField = new JTextField(c.bgColor, 7);
        bgField.setEnabled(!c.transparentBg);
        transBgBox.addActionListener(a -> {
            c.transparentBg = transBgBox.isSelected();
            bgField.setEnabled(!c.transparentBg);
        });
        bgField.getDocument().addDocumentListener(docListener(() -> c.bgColor = bgField.getText().trim()));

        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bgRow.add(transBgBox);
        bgRow.add(new JLabel("Color:"));
        bgRow.add(bgField);

        JPanel bgWrapper = new JPanel(new BorderLayout());
        bgWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        bgWrapper.add(bgRow, BorderLayout.WEST);
        JPanel applyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        applyPanel.add(applyBtn);
        bgWrapper.add(applyPanel, BorderLayout.EAST);
        section.add(bgWrapper);

        // Apply button action
        applyBtn.addActionListener(a -> {
            c.screenX = intFrom(ox, 0);
            c.screenY = intFrom(oy, 0);
            c.screenW = intFrom(ow, 200);
            c.screenH = intFrom(oh, 200);
            c.captureX = clamp(intFrom(rx, 0), 0, o.thinBTWidth - 1);
            c.captureY = clamp(intFrom(ry, 0), 0, o.thinBTHeight - 1);
            c.captureW = clamp(intFrom(rw, 200), 1, o.thinBTWidth - c.captureX);
            c.captureH = clamp(intFrom(rh, 200), 1, o.thinBTHeight - c.captureY);
            c.textThreshold = clamp(intFrom(threshField, 200), 0, 255);

            ox.setText(String.valueOf(c.screenX));
            oy.setText(String.valueOf(c.screenY));
            ow.setText(String.valueOf(c.screenW));
            oh.setText(String.valueOf(c.screenH));
            rx.setText(String.valueOf(c.captureX));
            ry.setText(String.valueOf(c.captureY));
            rw.setText(String.valueOf(c.captureW));
            rh.setText(String.valueOf(c.captureH));
            threshField.setText(String.valueOf(c.textThreshold));
        });

        return section;
    }

    /**
     * Rebuilds all capture panels from scratch.
     */
    private void rebuildCaptures() {
        capturesContainer.removeAll();
        capturePanels.clear();

        ThinCaptureOptions o = ThinCapture.getOptions();
        for (int i = 0; i < o.captures.size(); i++) {
            JPanel panel = buildCapturePanel(i);
            capturePanels.add(panel);
            capturesContainer.add(panel);
            capturesContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        capturesContainer.revalidate();
        capturesContainer.repaint();
    }

    public void onSwitchTo() {
        rebuildCaptures();
    }

    // --- Utility ---

    private static JTextField field(int val) { return new JTextField(String.valueOf(val), 4); }

    private static int intFrom(JTextField f, int fallback) {
        String t = f.getText().trim();
        boolean neg = t.startsWith("-");
        String nums = IntStream.range(0, t.length()).mapToObj(i -> t.charAt(i))
                .filter(Character::isDigit).map(String::valueOf).collect(Collectors.joining());
        return nums.isEmpty() ? fallback : (neg ? -1 : 1) * Integer.parseInt(nums);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static DocumentListener docListener(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) {}
        };
    }
}