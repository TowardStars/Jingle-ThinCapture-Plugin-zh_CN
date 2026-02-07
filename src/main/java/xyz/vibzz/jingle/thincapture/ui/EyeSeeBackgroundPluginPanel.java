package xyz.vibzz.jingle.thincapture.ui;

import xyz.vibzz.jingle.thincapture.ThinCapture;
import xyz.vibzz.jingle.thincapture.ThinCaptureOptions;
import xyz.vibzz.jingle.thincapture.config.BackgroundConfig;
import xyz.vibzz.jingle.thincapture.frame.BackgroundFrame;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EyeSeeBackgroundPluginPanel {
    public final JPanel mainPanel;
    private final JPanel backgroundsContainer;

    public EyeSeeBackgroundPluginPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        mainPanel.add(buildGeneralPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        backgroundsContainer = new JPanel();
        backgroundsContainer.setLayout(new BoxLayout(backgroundsContainer, BoxLayout.Y_AXIS));
        backgroundsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(backgroundsContainer);

        mainPanel.add(buildAddButtonRow());
        mainPanel.add(Box.createVerticalGlue());

        rebuildBackgrounds();
    }

    private JPanel buildGeneralPanel() {
        ThinCaptureOptions o = ThinCapture.getOptions();

        JPanel generalPanel = new JPanel();
        generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
        generalPanel.setBorder(BorderFactory.createTitledBorder("EyeSee Background Settings"));
        generalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox enableBox = new JCheckBox("Enable EyeSee Backgrounds");
        enableBox.setSelected(o.eyeSeeEnabled);
        enableBox.addActionListener(a -> o.eyeSeeEnabled = enableBox.isSelected());

        JLabel desc = new JLabel("Shows/hides with EyeSee projector toggle");
        desc.setFont(desc.getFont().deriveFont(Font.ITALIC, 11f));

        generalPanel.add(createRow(enableBox, desc));
        return generalPanel;
    }

    private JPanel buildAddButtonRow() {
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton addBgBtn = new JButton("+ Add Background");
        addBgBtn.addActionListener(a -> {
            String name = JOptionPane.showInputDialog(mainPanel, "Background name:", "Background");
            if (name != null && !name.trim().isEmpty()) {
                ThinCapture.addEyeSeeBackground(name.trim());
                rebuildBackgrounds();
            }
        });
        addRow.add(addBgBtn);

        return addRow;
    }

    private JPanel buildBackgroundPanel(int index) {
        ThinCaptureOptions o = ThinCapture.getOptions();
        BackgroundConfig bg = o.eyeSeeBackgrounds.get(index);

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(bg.name));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(buildTopRow(index, bg));
        section.add(buildImageRow(index, bg));
        section.add(buildPositionRow(index, bg));

        return section;
    }

    private JPanel buildTopRow(int index, BackgroundConfig bg) {
        JCheckBox enableBox = new JCheckBox("Enabled");
        enableBox.setSelected(bg.enabled);
        enableBox.addActionListener(a -> bg.enabled = enableBox.isSelected());

        JButton renameBtn = createSmallButton("Rename", a -> {
            String newName = JOptionPane.showInputDialog(mainPanel, "New name:", bg.name);
            if (newName != null && !newName.trim().isEmpty()) {
                ThinCapture.renameEyeSeeBackground(index, newName.trim());
                rebuildBackgrounds();
            }
        });

        JButton removeBtn = createRemoveButton("background \"" + bg.name + "\"", () -> {
            ThinCapture.removeEyeSeeBackground(index);
            rebuildBackgrounds();
        });

        return createRow(enableBox, renameBtn, removeBtn);
    }

    private JPanel buildImageRow(int index, BackgroundConfig bg) {
        JTextField bgPathField = new JTextField(bg.imagePath, 18);

        JButton browseBtn = createBrowseButton(path -> {
            bgPathField.setText(path);
            bg.imagePath = path;
            BackgroundFrame frame = ThinCapture.getEyeSeeBgFrame(index);
            if (frame != null) frame.loadImage(path);
        });

        JButton clearBtn = createSmallButton("Clear", a -> {
            bgPathField.setText("");
            bg.imagePath = "";
            BackgroundFrame frame = ThinCapture.getEyeSeeBgFrame(index);
            if (frame != null) frame.loadImage("");
        });

        return createRow(new JLabel("Image:"), bgPathField, browseBtn, clearBtn);
    }

    private JPanel buildPositionRow(int index, BackgroundConfig bg) {
        JTextField bgXField = new JTextField(String.valueOf(bg.x), 4);
        JTextField bgYField = new JTextField(String.valueOf(bg.y), 4);
        JTextField bgWField = new JTextField(String.valueOf(bg.width), 5);
        JTextField bgHField = new JTextField(String.valueOf(bg.height), 5);

        Consumer<Rectangle> onRegionSelected = r -> {
            bgXField.setText(String.valueOf(r.x));
            bgYField.setText(String.valueOf(r.y));
            bgWField.setText(String.valueOf(r.width));
            bgHField.setText(String.valueOf(r.height));
            bg.x = r.x;
            bg.y = r.y;
            bg.width = r.width;
            bg.height = r.height;
        };

        JButton selectBtn = createSmallButton("Select", a -> RegionSelector.selectOnScreen(onRegionSelected));

        JButton editBtn = createSmallButton("Edit", a -> {
            Rectangle current = new Rectangle(
                    intFrom(bgXField, 0), intFrom(bgYField, 0), intFrom(bgWField, 1920), intFrom(bgHField, 1080)
            );
            RegionSelector.editOnScreen(current, onRegionSelected);
        });

        JButton applyBtn = createSmallButton("Apply", a -> {
            bg.x = intFrom(bgXField, 0);
            bg.y = intFrom(bgYField, 0);
            bg.width = Math.max(1, intFrom(bgWField, 1920));
            bg.height = Math.max(1, intFrom(bgHField, 1080));

            bgXField.setText(String.valueOf(bg.x));
            bgYField.setText(String.valueOf(bg.y));
            bgWField.setText(String.valueOf(bg.width));
            bgHField.setText(String.valueOf(bg.height));

            BackgroundFrame frame = ThinCapture.getEyeSeeBgFrame(index);
            if (frame != null) frame.loadImage(bg.imagePath);
        });

        JPanel posRow = new JPanel(new BorderLayout());
        posRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        posRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel posRowLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        posRowLeft.add(new JLabel("X:"));
        posRowLeft.add(bgXField);
        posRowLeft.add(new JLabel("Y:"));
        posRowLeft.add(bgYField);
        posRowLeft.add(new JLabel("Width:"));
        posRowLeft.add(bgWField);
        posRowLeft.add(new JLabel("Height:"));
        posRowLeft.add(bgHField);
        posRowLeft.add(selectBtn);
        posRowLeft.add(editBtn);

        JPanel posRowRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        posRowRight.add(applyBtn);

        posRow.add(posRowLeft, BorderLayout.WEST);
        posRow.add(posRowRight, BorderLayout.EAST);

        return posRow;
    }

    private void rebuildBackgrounds() {
        backgroundsContainer.removeAll();

        ThinCaptureOptions o = ThinCapture.getOptions();

        for (int i = 0; i < o.eyeSeeBackgrounds.size(); i++) {
            backgroundsContainer.add(buildBackgroundPanel(i));
            backgroundsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        backgroundsContainer.revalidate();
        backgroundsContainer.repaint();
    }

    public void onSwitchTo() {
        rebuildBackgrounds();
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
        JButton removeBtn = new JButton("Remove");
        removeBtn.setMargin(new Insets(1, 6, 1, 6));
        removeBtn.setForeground(Color.RED);
        removeBtn.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "Remove " + label + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                onConfirm.run();
            }
        });
        return removeBtn;
    }

    private JButton createBrowseButton(Consumer<String> onFileSelected) {
        JButton browseBtn = new JButton("Browse...");
        browseBtn.setMargin(new Insets(1, 6, 1, 6));
        browseBtn.addActionListener(a -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Images (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"
            ));
            if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
                onFileSelected.accept(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return browseBtn;
    }

    private static int intFrom(JTextField f, int fallback) {
        String t = f.getText().trim();
        boolean neg = t.startsWith("-");
        String nums = IntStream.range(0, t.length()).mapToObj(t::charAt)
                .filter(Character::isDigit).map(String::valueOf).collect(Collectors.joining());
        return nums.isEmpty() ? fallback : (neg ? -1 : 1) * Integer.parseInt(nums);
    }
}