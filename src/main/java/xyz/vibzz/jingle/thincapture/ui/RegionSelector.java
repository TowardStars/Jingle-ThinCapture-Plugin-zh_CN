package xyz.vibzz.jingle.thincapture.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.function.Consumer;

/**
 * A fullscreen transparent overlay that lets the user drag-select or edit a rectangle.
 * Supports two modes:
 *   - Create mode: drag to draw a new rectangle
 *   - Edit mode: move/resize an existing rectangle via drag and corner handles
 *
 * Spans all monitors (virtual screen) so captures can be placed on any display.
 */
public class RegionSelector extends JFrame {
    private static final int HANDLE_SIZE = 8;
    private static final int EDGE_TOLERANCE = 6;
    private static final int MOVE_HANDLE_SIZE = 24;

    private Point startPoint = null;
    private Point endPoint = null;
    private final Consumer<Rectangle> onSelected;

    /** The origin of the virtual screen bounds — used to convert between overlay-local and absolute coords. */
    private final int originX;
    private final int originY;

    // Edit mode state
    private final boolean editMode;
    private Rectangle editRect;
    private DragType dragType = DragType.NONE;
    private Point dragStart = null;
    private Rectangle dragOrigRect = null;

    private enum DragType {
        NONE, MOVE,
        RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE,
        RESIZE_N, RESIZE_S, RESIZE_W, RESIZE_E
    }

    /**
     * Create mode constructor — drag to select a new region.
     */
    public RegionSelector(String title, Rectangle bounds, Consumer<Rectangle> onSelected) {
        this(title, bounds, null, onSelected);
    }

    /**
     * Full constructor — if initialRect is non-null, starts in edit mode.
     * All coordinates in initialRect and the callback result are in absolute screen coords.
     * Internally the overlay works in overlay-local coords (0,0 = top-left of the overlay).
     */
    public RegionSelector(String title, Rectangle bounds, Rectangle initialRect, Consumer<Rectangle> onSelected) {
        super();
        this.originX = bounds.x;
        this.originY = bounds.y;

        // Wrap the callback to convert overlay-local coords back to absolute screen coords
        this.onSelected = r -> onSelected.accept(new Rectangle(
                r.x + originX,
                r.y + originY,
                r.width,
                r.height
        ));

        // Convert initialRect from absolute screen coords to overlay-local coords
        if (initialRect != null) {
            this.editMode = true;
            this.editRect = new Rectangle(
                    initialRect.x - originX,
                    initialRect.y - originY,
                    initialRect.width,
                    initialRect.height
            );
        } else {
            this.editMode = false;
            this.editRect = null;
        }

        this.setUndecorated(true);
        this.setAlwaysOnTop(true);
        this.setBackground(new Color(0, 0, 0, 80));
        this.setBounds(bounds);
        this.setTitle(title);

        JPanel overlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillRect(0, 0, getWidth(), getHeight());

                Rectangle r = getCurrentRect();
                if (r != null && r.width > 0 && r.height > 0) {
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(r.x, r.y, r.width, r.height);
                    g2.setComposite(AlphaComposite.SrcOver);

                    g2.setColor(Color.RED);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(r.x, r.y, r.width, r.height);

                    // Show absolute screen coords in the label so the user sees real positions
                    String label = (r.x + originX) + ", " + (r.y + originY) + "  —  " + r.width + " x " + r.height;
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                    FontMetrics fm = g2.getFontMetrics();
                    int labelX = r.x + (r.width - fm.stringWidth(label)) / 2;
                    int labelY = r.y - 6;
                    if (labelY < 16) labelY = r.y + r.height + fm.getHeight() + 4;
                    g2.drawString(label, labelX, labelY);

                    if (editMode) {
                        drawHandles(g2, r);
                    }
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
                String msg = editMode
                        ? "Drag to move. Drag corners/edges to resize. Press ENTER to confirm, ESC to cancel."
                        : "Click and drag to select. Press ESC to cancel.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, 30);
            }
        };
        overlay.setOpaque(false);
        this.setContentPane(overlay);

        if (editMode) {
            setupEditListeners(overlay);
        } else {
            setupCreateListeners(overlay);
        }

        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && editMode && editRect != null) {
                    dispose();
                    RegionSelector.this.onSelected.accept(new Rectangle(editRect));
                }
            }
        });

        this.setVisible(true);
        this.requestFocus();
    }

    private Rectangle getCurrentRect() {
        if (editMode) return editRect;
        if (startPoint != null && endPoint != null) return getSelectionRect();
        return null;
    }

    private void setupCreateListeners(JPanel overlay) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        overlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                endPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                endPoint = e.getPoint();
                Rectangle r = getSelectionRect();
                if (r.width > 2 && r.height > 2) {
                    dispose();
                    onSelected.accept(r);
                }
            }
        });

        overlay.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                endPoint = e.getPoint();
                overlay.repaint();
            }
        });
    }

    private void setupEditListeners(JPanel overlay) {
        overlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (editRect == null) return;
                dragStart = e.getPoint();
                dragOrigRect = new Rectangle(editRect);
                dragType = hitTest(e.getPoint(), editRect);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragType = DragType.NONE;
                dragStart = null;
                dragOrigRect = null;
            }
        });

        overlay.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragType == DragType.NONE || dragStart == null || dragOrigRect == null) return;

                int dx = e.getX() - dragStart.x;
                int dy = e.getY() - dragStart.y;

                Rectangle r = new Rectangle(dragOrigRect);

                switch (dragType) {
                    case MOVE:
                        r.x += dx;
                        r.y += dy;
                        break;
                    case RESIZE_NW:
                        r.x += dx; r.y += dy; r.width -= dx; r.height -= dy;
                        break;
                    case RESIZE_NE:
                        r.y += dy; r.width += dx; r.height -= dy;
                        break;
                    case RESIZE_SW:
                        r.x += dx; r.width -= dx; r.height += dy;
                        break;
                    case RESIZE_SE:
                        r.width += dx; r.height += dy;
                        break;
                    case RESIZE_N:
                        r.y += dy; r.height -= dy;
                        break;
                    case RESIZE_S:
                        r.height += dy;
                        break;
                    case RESIZE_W:
                        r.x += dx; r.width -= dx;
                        break;
                    case RESIZE_E:
                        r.width += dx;
                        break;
                }

                if (r.width >= 4 && r.height >= 4) {
                    editRect = r;
                }

                overlay.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (editRect == null) return;
                DragType hit = hitTest(e.getPoint(), editRect);
                overlay.setCursor(getCursorForDragType(hit));
            }
        });
    }

    private DragType hitTest(Point p, Rectangle r) {
        int x = p.x, y = p.y;

        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int mhs = MOVE_HANDLE_SIZE / 2;
        if (x >= cx - mhs && x <= cx + mhs && y >= cy - mhs && y <= cy + mhs) {
            return DragType.MOVE;
        }

        boolean nearLeft   = Math.abs(x - r.x) <= EDGE_TOLERANCE;
        boolean nearRight  = Math.abs(x - (r.x + r.width)) <= EDGE_TOLERANCE;
        boolean nearTop    = Math.abs(y - r.y) <= EDGE_TOLERANCE;
        boolean nearBottom = Math.abs(y - (r.y + r.height)) <= EDGE_TOLERANCE;
        boolean inX = x >= r.x - EDGE_TOLERANCE && x <= r.x + r.width + EDGE_TOLERANCE;
        boolean inY = y >= r.y - EDGE_TOLERANCE && y <= r.y + r.height + EDGE_TOLERANCE;

        if (nearLeft && nearTop) return DragType.RESIZE_NW;
        if (nearRight && nearTop) return DragType.RESIZE_NE;
        if (nearLeft && nearBottom) return DragType.RESIZE_SW;
        if (nearRight && nearBottom) return DragType.RESIZE_SE;

        if (nearTop && inX) return DragType.RESIZE_N;
        if (nearBottom && inX) return DragType.RESIZE_S;
        if (nearLeft && inY) return DragType.RESIZE_W;
        if (nearRight && inY) return DragType.RESIZE_E;

        return DragType.NONE;
    }

    private Cursor getCursorForDragType(DragType dt) {
        switch (dt) {
            case MOVE:      return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            case RESIZE_NW: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case RESIZE_NE: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case RESIZE_SW: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case RESIZE_SE: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            case RESIZE_N:  return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case RESIZE_S:  return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case RESIZE_W:  return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case RESIZE_E:  return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            default:        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private void drawHandles(Graphics2D g2, Rectangle r) {
        g2.setColor(Color.WHITE);
        int hs = HANDLE_SIZE;
        drawHandle(g2, r.x, r.y, hs);
        drawHandle(g2, r.x + r.width, r.y, hs);
        drawHandle(g2, r.x, r.y + r.height, hs);
        drawHandle(g2, r.x + r.width, r.y + r.height, hs);
        drawHandle(g2, r.x + r.width / 2, r.y, hs);
        drawHandle(g2, r.x + r.width / 2, r.y + r.height, hs);
        drawHandle(g2, r.x, r.y + r.height / 2, hs);
        drawHandle(g2, r.x + r.width, r.y + r.height / 2, hs);

        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int ms = MOVE_HANDLE_SIZE;
        int mhs = ms / 2;

        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillRoundRect(cx - mhs, cy - mhs, ms, ms, 4, 4);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(cx - mhs, cy - mhs, ms, ms, 4, 4);

        g2.setColor(new Color(60, 60, 60));
        g2.setStroke(new BasicStroke(1.5f));
        int arrowLen = ms / 3;
        int tipSize = 3;
        g2.drawLine(cx, cy - arrowLen, cx, cy + arrowLen);
        g2.drawLine(cx, cy - arrowLen, cx - tipSize, cy - arrowLen + tipSize);
        g2.drawLine(cx, cy - arrowLen, cx + tipSize, cy - arrowLen + tipSize);
        g2.drawLine(cx, cy + arrowLen, cx - tipSize, cy + arrowLen - tipSize);
        g2.drawLine(cx, cy + arrowLen, cx + tipSize, cy + arrowLen - tipSize);
        g2.drawLine(cx - arrowLen, cy, cx + arrowLen, cy);
        g2.drawLine(cx - arrowLen, cy, cx - arrowLen + tipSize, cy - tipSize);
        g2.drawLine(cx - arrowLen, cy, cx - arrowLen + tipSize, cy + tipSize);
        g2.drawLine(cx + arrowLen, cy, cx + arrowLen - tipSize, cy - tipSize);
        g2.drawLine(cx + arrowLen, cy, cx + arrowLen - tipSize, cy + tipSize);

        g2.setStroke(new BasicStroke(2));
    }

    private void drawHandle(Graphics2D g2, int cx, int cy, int size) {
        g2.fillRect(cx - size / 2, cy - size / 2, size, size);
        g2.setColor(Color.RED);
        g2.drawRect(cx - size / 2, cy - size / 2, size, size);
        g2.setColor(Color.WHITE);
    }

    private Rectangle getSelectionRect() {
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int w = Math.abs(endPoint.x - startPoint.x);
        int h = Math.abs(endPoint.y - startPoint.y);
        return new Rectangle(x, y, w, h);
    }

    // ===== Static helpers =====

    /**
     * Returns the bounding rectangle that spans all monitors (the virtual screen).
     * Coordinates may be negative if monitors are arranged to the left of or above the primary.
     */
    private static Rectangle getVirtualScreenBounds() {
        Rectangle virtualBounds = new Rectangle();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            for (GraphicsConfiguration gc : gd.getConfigurations()) {
                virtualBounds = virtualBounds.union(gc.getBounds());
            }
        }
        return virtualBounds;
    }

    /**
     * Finds the display scale factors for the monitor that contains the given
     * physical screen point. Falls back to the default screen if no match is found.
     */
    private static double[] getScaleForPhysicalPoint(int physX, int physY) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle screenBounds = gc.getBounds();
            AffineTransform tx = gc.getDefaultTransform();
            Rectangle physBounds = new Rectangle(
                    (int) (screenBounds.x * tx.getScaleX()),
                    (int) (screenBounds.y * tx.getScaleY()),
                    (int) (screenBounds.width * tx.getScaleX()),
                    (int) (screenBounds.height * tx.getScaleY())
            );
            if (physBounds.contains(physX, physY)) {
                return new double[]{ tx.getScaleX(), tx.getScaleY() };
            }
        }
        AffineTransform tx = ge.getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform();
        return new double[]{ tx.getScaleX(), tx.getScaleY() };
    }

    private static double getDisplayScaleX() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                .getDefaultTransform().getScaleX();
    }

    private static double getDisplayScaleY() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                .getDefaultTransform().getScaleY();
    }

    public static void selectOnScreen(Consumer<Rectangle> onSelected) {
        Rectangle virtualBounds = getVirtualScreenBounds();
        new RegionSelector("Select Monitor Position", virtualBounds, onSelected);
    }

    public static void editOnScreen(Rectangle current, Consumer<Rectangle> onSelected) {
        Rectangle virtualBounds = getVirtualScreenBounds();
        new RegionSelector("Edit Monitor Position", virtualBounds, current, onSelected);
    }

    /**
     * Opens a region selector overlay positioned over the MC window's client area.
     * The returned rectangle is in MC client pixel coordinates.
     */
    public static void selectOnMCWindow(Consumer<Rectangle> onSelected) {
        if (!xyz.duncanruns.jingle.Jingle.getMainInstance().isPresent()) {
            JOptionPane.showMessageDialog(null, "No Minecraft instance detected.", "ThinCapture", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.sun.jna.platform.win32.WinDef.HWND hwnd = xyz.duncanruns.jingle.Jingle.getMainInstance().get().hwnd;

        Rectangle mcBounds = getMCClientBoundsInLogicalCoords(hwnd);
        if (mcBounds == null) return;

        com.sun.jna.platform.win32.WinDef.RECT clientRect = new com.sun.jna.platform.win32.WinDef.RECT();
        xyz.duncanruns.jingle.win32.User32.INSTANCE.GetClientRect(hwnd, clientRect);
        int clientW = clientRect.right - clientRect.left;
        int clientH = clientRect.bottom - clientRect.top;

        double toClientX = mcBounds.width > 0 ? (double) clientW / mcBounds.width : 1.0;
        double toClientY = mcBounds.height > 0 ? (double) clientH / mcBounds.height : 1.0;

        // For MC window selection, the overlay is positioned exactly over the MC client area.
        // The callback from RegionSelector already adds originX/originY (the mcBounds origin),
        // but we need coords relative to the MC client area (starting at 0,0).
        // So we subtract mcBounds origin before scaling to client pixels.
        new RegionSelector("Select MC Region", mcBounds, r -> {
            int relX = r.x - mcBounds.x;
            int relY = r.y - mcBounds.y;
            onSelected.accept(new Rectangle(
                    (int) (relX * toClientX),
                    (int) (relY * toClientY),
                    (int) (r.width * toClientX),
                    (int) (r.height * toClientY)
            ));
        });
    }

    public static void editOnMCWindow(Rectangle current, Consumer<Rectangle> onSelected) {
        if (!xyz.duncanruns.jingle.Jingle.getMainInstance().isPresent()) {
            JOptionPane.showMessageDialog(null, "No Minecraft instance detected.", "ThinCapture", JOptionPane.WARNING_MESSAGE);
            return;
        }
        com.sun.jna.platform.win32.WinDef.HWND hwnd = xyz.duncanruns.jingle.Jingle.getMainInstance().get().hwnd;

        Rectangle mcBounds = getMCClientBoundsInLogicalCoords(hwnd);
        if (mcBounds == null) return;

        com.sun.jna.platform.win32.WinDef.RECT clientRect = new com.sun.jna.platform.win32.WinDef.RECT();
        xyz.duncanruns.jingle.win32.User32.INSTANCE.GetClientRect(hwnd, clientRect);
        int clientW = clientRect.right - clientRect.left;
        int clientH = clientRect.bottom - clientRect.top;

        double toClientX = mcBounds.width > 0 ? (double) clientW / mcBounds.width : 1.0;
        double toClientY = mcBounds.height > 0 ? (double) clientH / mcBounds.height : 1.0;
        double toOverlayX = toClientX > 0 ? 1.0 / toClientX : 1.0;
        double toOverlayY = toClientY > 0 ? 1.0 / toClientY : 1.0;

        // Convert current MC client rect to absolute screen coords for the edit overlay.
        // current is in MC client pixels (0,0 = top-left of client area).
        // We convert to absolute logical screen coords so the RegionSelector constructor
        // can subtract the overlay origin correctly.
        Rectangle absoluteRect = new Rectangle(
                mcBounds.x + (int) (current.x * toOverlayX),
                mcBounds.y + (int) (current.y * toOverlayY),
                (int) (current.width * toOverlayX),
                (int) (current.height * toOverlayY)
        );

        new RegionSelector("Edit MC Region", mcBounds, absoluteRect, r -> {
            int relX = r.x - mcBounds.x;
            int relY = r.y - mcBounds.y;
            onSelected.accept(new Rectangle(
                    (int) (relX * toClientX),
                    (int) (relY * toClientY),
                    (int) (r.width * toClientX),
                    (int) (r.height * toClientY)
            ));
        });
    }

    /**
     * Computes the MC window's client area bounds in Java logical coordinates.
     * Uses the DPI scale of the monitor the window is actually on, supporting
     * mixed-DPI multi-monitor setups.
     */
    private static Rectangle getMCClientBoundsInLogicalCoords(com.sun.jna.platform.win32.WinDef.HWND hwnd) {
        com.sun.jna.platform.win32.WinDef.RECT winRect = new com.sun.jna.platform.win32.WinDef.RECT();
        xyz.duncanruns.jingle.win32.User32.INSTANCE.GetWindowRect(hwnd, winRect);
        int winW = winRect.right - winRect.left;
        int winH = winRect.bottom - winRect.top;

        com.sun.jna.platform.win32.WinDef.RECT clientRect = new com.sun.jna.platform.win32.WinDef.RECT();
        xyz.duncanruns.jingle.win32.User32.INSTANCE.GetClientRect(hwnd, clientRect);
        int clientW = clientRect.right - clientRect.left;
        int clientH = clientRect.bottom - clientRect.top;

        int style = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(hwnd, -16);
        int exStyle = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(hwnd, -20);

        com.sun.jna.platform.win32.WinDef.RECT frameRect = new com.sun.jna.platform.win32.WinDef.RECT();
        frameRect.left = 0; frameRect.top = 0; frameRect.right = 0; frameRect.bottom = 0;
        com.sun.jna.platform.win32.User32.INSTANCE.AdjustWindowRectEx(
                frameRect,
                new com.sun.jna.platform.win32.WinDef.DWORD(style),
                new com.sun.jna.platform.win32.WinDef.BOOL(false),
                new com.sun.jna.platform.win32.WinDef.DWORD(exStyle)
        );
        int borderLeft = -frameRect.left;
        int borderTop = -frameRect.top;

        int clientScreenX = winRect.left + borderLeft;
        int clientScreenY = winRect.top + borderTop;
        int physClientW = winW - borderLeft - frameRect.right;
        int physClientH = winH - borderTop - frameRect.bottom;

        int windowCenterX = winRect.left + winW / 2;
        int windowCenterY = winRect.top + winH / 2;
        double[] scale = getScaleForPhysicalPoint(windowCenterX, windowCenterY);
        double scaleX = scale[0];
        double scaleY = scale[1];

        return new Rectangle(
                (int) (clientScreenX / scaleX),
                (int) (clientScreenY / scaleY),
                (int) (physClientW / scaleX),
                (int) (physClientH / scaleY)
        );
    }
}