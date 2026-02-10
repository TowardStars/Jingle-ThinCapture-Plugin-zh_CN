package xyz.vibzz.jingle.thincapture.ui;

import xyz.vibzz.jingle.thincapture.ThinCapture;
import xyz.vibzz.jingle.thincapture.ThinCaptureOptions;
import xyz.vibzz.jingle.thincapture.config.CaptureConfig;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ThinCapturePluginPanel {
    public final JPanel mainPanel;
    private final JPanel capturesContainer;
    private JLabel sizeLabel;

    public ThinCapturePluginPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        mainPanel.add(buildGeneralPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        capturesContainer = new JPanel();
        capturesContainer.setLayout(new BoxLayout(capturesContainer, BoxLayout.Y_AXIS));
        capturesContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(capturesContainer);

        mainPanel.add(buildAddButtonRow());
        mainPanel.add(Box.createVerticalGlue());

        rebuildCaptures();
    }

    private JPanel buildGeneralPanel() {
        ThinCaptureOptions o = ThinCapture.getOptions();

        JPanel generalPanel = new JPanel();
        generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
        generalPanel.setBorder(BorderFactory.createTitledBorder("常规"));
        generalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        generalPanel.add(buildAmdRow(o));
        generalPanel.add(buildSizeRow());
        generalPanel.add(buildFpsRow(o));

        return generalPanel;
    }

    private JPanel buildAmdRow(ThinCaptureOptions o) {
        JCheckBox amdBox = new JCheckBox("AMD显卡兼容模式");
        amdBox.setSelected(o.amdCompatMode);
        amdBox.addActionListener(a -> o.amdCompatMode = amdBox.isSelected());

        JLabel desc = new JLabel("如果在AMD显卡上，采集画面显示为黑屏，开启该选项。 [影响全局]");
        desc.setFont(desc.getFont().deriveFont(Font.ITALIC, 11f));

        return createRow(amdBox, desc);
    }

    private JPanel buildSizeRow() {
        sizeLabel = new JLabel();
        refreshSizeLabel();

        JLabel desc = new JLabel("(已从‘调整窗口大小[Resizing]’脚本同步)");
        desc.setFont(desc.getFont().deriveFont(Font.ITALIC, 11f));

        return createRow(new JLabel("宝藏宏大小："), sizeLabel, desc);
    }

    private void refreshSizeLabel() {
        if (sizeLabel != null) {
            int w = ThinCapture.getEffectiveThinBTWidth();
            int h = ThinCapture.getEffectiveThinBTHeight();
            sizeLabel.setText(w + " \u00d7 " + h);
        }
    }

    private JPanel buildFpsRow(ThinCaptureOptions o) {
        JTextField fpsField = new JTextField(String.valueOf(o.fpsLimit), 4);
        fpsField.getDocument().addDocumentListener(docListener(() -> {
            o.fpsLimit = clamp(intFrom(fpsField, 30), 5, 240);
            ThinCapture.updateFpsLimit();
        }));

        return createRow(new JLabel("帧率限制："), fpsField);
    }

    private JPanel buildAddButtonRow() {
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton addBtn = new JButton("+ 添加采集");
        addBtn.addActionListener(a -> {
            String name = JOptionPane.showInputDialog(mainPanel, "采集名称：", "新采集");
            if (name != null && !name.trim().isEmpty()) {
                ThinCapture.addCapture(name.trim());
                rebuildCaptures();
            }
        });
        addRow.add(addBtn);

        return addRow;
    }

    // ===== Capture Panel =====

    private JPanel buildCapturePanel(int index) {
        ThinCaptureOptions o = ThinCapture.getOptions();
        CaptureConfig c = o.captures.get(index);

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(c.name));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(buildCaptureTopRow(index, c));
        section.add(buildMonitorRow(index, c));
        section.add(buildMCRegionRow(index, c));
        section.add(buildTransparencySection(index, c));

        return section;
    }

    private JPanel buildCaptureTopRow(int index, CaptureConfig c) {
        JCheckBox enableBox = new JCheckBox("开启");
        enableBox.setSelected(c.enabled);
        enableBox.addActionListener(a -> {
            c.enabled = enableBox.isSelected();
            ThinCapture.setCaptureEnabled(index, c.enabled);
        });

        JButton renameBtn = createSmallButton("重命名", a -> {
            String newName = JOptionPane.showInputDialog(mainPanel, "新名称", c.name);
            if (newName != null && !newName.trim().isEmpty()) {
                ThinCapture.renameCapture(index, newName.trim());
                rebuildCaptures();
            }
        });

        JButton removeBtn = createRemoveButton("采集 \"" + c.name + "\"", () -> {
            ThinCapture.removeCapture(index);
            rebuildCaptures();
        });

        return createRow(enableBox, renameBtn, removeBtn);
    }

    private JPanel buildMonitorRow(int index, CaptureConfig c) {
        JTextField ox = field(c.screenX), oy = field(c.screenY), ow = field(c.screenW), oh = field(c.screenH);

        Runnable applyMonitor = () -> {
            c.screenX = intFrom(ox, 0);
            c.screenY = intFrom(oy, 0);
            c.screenW = Math.max(1, intFrom(ow, 200));
            c.screenH = Math.max(1, intFrom(oh, 200));
            ThinCapture.repositionCapture(index);
        };

        Consumer<Rectangle> onRegionSelected = r -> {
            ox.setText(String.valueOf(r.x));
            oy.setText(String.valueOf(r.y));
            ow.setText(String.valueOf(r.width));
            oh.setText(String.valueOf(r.height));
            c.screenX = r.x;
            c.screenY = r.y;
            c.screenW = r.width;
            c.screenH = r.height;
            ThinCapture.repositionCapture(index);
        };

        ox.getDocument().addDocumentListener(docListener(applyMonitor));
        oy.getDocument().addDocumentListener(docListener(applyMonitor));
        ow.getDocument().addDocumentListener(docListener(applyMonitor));
        oh.getDocument().addDocumentListener(docListener(applyMonitor));

        JButton selectBtn = createSmallButton("选区", a -> RegionSelector.selectOnScreen(onRegionSelected));

        JButton editBtn = createSmallButton("编辑", a -> {
            Rectangle current = new Rectangle(intFrom(ox, 0), intFrom(oy, 0), intFrom(ow, 200), intFrom(oh, 200));
            RegionSelector.editOnScreen(current, onRegionSelected);
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        row.add(new JLabel("投影位置  水平位置:"));
        row.add(ox);
        row.add(new JLabel("垂直位置:"));
        row.add(oy);
        row.add(new JLabel("宽度:"));
        row.add(ow);
        row.add(new JLabel("高度:"));
        row.add(oh);
        row.add(selectBtn);
        row.add(editBtn);

        return row;
    }

    private JPanel buildMCRegionRow(int index, CaptureConfig c) {
        JTextField rx = field(c.captureX), ry = field(c.captureY), rw = field(c.captureW), rh = field(c.captureH);

        Runnable applyRegion = () -> {
            int effW = ThinCapture.getEffectiveThinBTWidth();
            int effH = ThinCapture.getEffectiveThinBTHeight();
            c.captureX = clamp(intFrom(rx, 0), 0, effW - 1);
            c.captureY = clamp(intFrom(ry, 0), 0, effH - 1);
            c.captureW = clamp(Math.max(1, intFrom(rw, 200)), 1, effW - c.captureX);
            c.captureH = clamp(Math.max(1, intFrom(rh, 200)), 1, effH - c.captureY);
            ThinCapture.repositionCapture(index);
        };

        Consumer<Rectangle> onRegionSelected = r -> {
            rx.setText(String.valueOf(r.x));
            ry.setText(String.valueOf(r.y));
            rw.setText(String.valueOf(r.width));
            rh.setText(String.valueOf(r.height));
            c.captureX = r.x;
            c.captureY = r.y;
            c.captureW = r.width;
            c.captureH = r.height;
            ThinCapture.repositionCapture(index);
        };

        rx.getDocument().addDocumentListener(docListener(applyRegion));
        ry.getDocument().addDocumentListener(docListener(applyRegion));
        rw.getDocument().addDocumentListener(docListener(applyRegion));
        rh.getDocument().addDocumentListener(docListener(applyRegion));

        JButton selectBtn = createSmallButton("选区", a -> RegionSelector.selectOnMCWindow(onRegionSelected));

        JButton editBtn = createSmallButton("编辑", a -> {
            Rectangle current = new Rectangle(intFrom(rx, 0), intFrom(ry, 0), intFrom(rw, 200), intFrom(rh, 200));
            RegionSelector.editOnMCWindow(current, onRegionSelected);
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        row.add(new JLabel("MC  区域 水平位置:"));
        row.add(rx);
        row.add(new JLabel("垂直位置:"));
        row.add(ry);
        row.add(new JLabel("宽度:"));
        row.add(rw);
        row.add(new JLabel("高度:"));
        row.add(rh);
        row.add(selectBtn);
        row.add(editBtn);

        return row;
    }

    private JPanel buildTransparencySection(int index, CaptureConfig c) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("透明度"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JCheckBox transparencyBox = new JCheckBox("开启 (突出白色文本)");
        transparencyBox.setSelected(c.textOnly);

        JLabel threshLabel = new JLabel("阈值：");
        JTextField threshField = new JTextField(String.valueOf(c.textThreshold), 3);
        JLabel threshNote = new JLabel("[0-255]");
        threshNote.setFont(threshNote.getFont().deriveFont(Font.ITALIC, 10f));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row1.add(transparencyBox);
        row1.add(Box.createHorizontalStrut(8));
        row1.add(threshLabel);
        row1.add(threshField);
        row1.add(threshNote);
        section.add(row1);

        JRadioButton bgTransparentRadio = new JRadioButton("透明");
        JRadioButton bgColorRadio = new JRadioButton("纯色");
        JRadioButton bgImageRadio = new JRadioButton("图片");
        ButtonGroup bgGroup = new ButtonGroup();
        bgGroup.add(bgTransparentRadio);
        bgGroup.add(bgColorRadio);
        bgGroup.add(bgImageRadio);

        if (c.transparentBg) {
            bgTransparentRadio.setSelected(true);
        } else if (c.bgImagePath != null && !c.bgImagePath.trim().isEmpty()) {
            bgImageRadio.setSelected(true);
        } else {
            bgColorRadio.setSelected(true);
        }

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row2.add(new JLabel("背景："));
        row2.add(bgTransparentRadio);
        row2.add(bgColorRadio);
        row2.add(bgImageRadio);
        section.add(row2);

        JLabel colorLabel = new JLabel("十六进制颜色代码：");
        JTextField bgField = new JTextField(c.bgColor, 7);
        JTextField bgImageField = new JTextField(c.bgImagePath, 14);

        JButton browseBtn = createSmallButton("浏览...", a -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "图片 (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"
            ));
            if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                bgImageField.setText(path);
                c.bgImagePath = path;
                ThinCapture.updateCaptureFilter(index);
            }
        });

        JButton clearImgBtn = createSmallButton("清除", a -> {
            bgImageField.setText("");
            c.bgImagePath = "";
            ThinCapture.updateCaptureFilter(index);
        });

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row3.add(Box.createHorizontalStrut(16));
        row3.add(colorLabel);
        row3.add(bgField);
        row3.add(Box.createHorizontalStrut(12));
        row3.add(bgImageField);
        row3.add(browseBtn);
        row3.add(clearImgBtn);
        section.add(row3);

        Runnable updateState = () -> {
            boolean on = transparencyBox.isSelected();
            threshLabel.setEnabled(on);
            threshField.setEnabled(on);
            threshNote.setEnabled(on);
            bgTransparentRadio.setEnabled(on);
            bgColorRadio.setEnabled(on);
            bgImageRadio.setEnabled(on);

            boolean colorOn = on && bgColorRadio.isSelected();
            boolean imageOn = on && bgImageRadio.isSelected();
            colorLabel.setEnabled(colorOn);
            bgField.setEnabled(colorOn);
            bgImageField.setEnabled(imageOn);
            browseBtn.setEnabled(imageOn);
            clearImgBtn.setEnabled(imageOn);
        };

        Runnable syncAndApply = () -> {
            c.textOnly = transparencyBox.isSelected();
            c.transparentBg = bgTransparentRadio.isSelected();
            if (bgColorRadio.isSelected()) {
                c.bgImagePath = "";
            }
            ThinCapture.updateCaptureFilter(index);
        };

        transparencyBox.addActionListener(a -> { syncAndApply.run(); updateState.run(); });
        bgTransparentRadio.addActionListener(a -> { syncAndApply.run(); updateState.run(); });
        bgColorRadio.addActionListener(a -> { syncAndApply.run(); updateState.run(); });
        bgImageRadio.addActionListener(a -> { syncAndApply.run(); updateState.run(); });

        threshField.getDocument().addDocumentListener(docListener(() -> {
            c.textThreshold = clamp(intFrom(threshField, 200), 0, 255);
            ThinCapture.updateCaptureFilter(index);
        }));
        bgField.getDocument().addDocumentListener(docListener(() -> {
            c.bgColor = bgField.getText().trim();
            ThinCapture.updateCaptureFilter(index);
        }));
        bgImageField.getDocument().addDocumentListener(docListener(() -> {
            c.bgImagePath = bgImageField.getText().trim();
            ThinCapture.updateCaptureFilter(index);
        }));

        updateState.run();

        return section;
    }

    private void rebuildCaptures() {
        capturesContainer.removeAll();

        ThinCaptureOptions o = ThinCapture.getOptions();

        for (int i = 0; i < o.captures.size(); i++) {
            capturesContainer.add(buildCapturePanel(i));
            capturesContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        capturesContainer.revalidate();
        capturesContainer.repaint();
    }

    public void onSwitchTo() {
        refreshSizeLabel();
        rebuildCaptures();
    }

    // ===== UI Helpers =====

    private JPanel createRow(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component c : components) {
            row.add(c);
        }
        return row;
    }

    private JButton createSmallButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.addActionListener(action);
        return btn;
    }

    private JButton createRemoveButton(String label, Runnable onConfirm) {
        JButton removeBtn = new JButton("移除");
        removeBtn.setMargin(new Insets(1, 6, 1, 6));
        removeBtn.setForeground(Color.RED);
        removeBtn.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "移除 " + label + "？", "确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                onConfirm.run();
            }
        });
        return removeBtn;
    }

    private static JTextField field(int val) {
        return new JTextField(String.valueOf(val), 4);
    }

    private static int intFrom(JTextField f, int fallback) {
        String t = f.getText().trim();
        boolean neg = t.startsWith("-");
        String nums = IntStream.range(0, t.length()).mapToObj(t::charAt)
                .filter(Character::isDigit).map(String::valueOf).collect(Collectors.joining());
        return nums.isEmpty() ? fallback : (neg ? -1 : 1) * Integer.parseInt(nums);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static DocumentListener docListener(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) {}
        };
    }
}