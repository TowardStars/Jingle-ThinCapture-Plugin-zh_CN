package xyz.vibzz.jingle.thincapture;

import com.google.common.io.Resources;
import com.sun.jna.platform.win32.WinDef;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;
import xyz.duncanruns.jingle.win32.User32;
import xyz.vibzz.jingle.thincapture.config.BackgroundConfig;
import xyz.vibzz.jingle.thincapture.config.CaptureConfig;
import xyz.vibzz.jingle.thincapture.frame.BackgroundFrame;
import xyz.vibzz.jingle.thincapture.frame.CaptureFrame;
import xyz.vibzz.jingle.thincapture.ui.BackgroundsPluginPanel;
import xyz.vibzz.jingle.thincapture.ui.PlanarAbusePluginPanel;
import xyz.vibzz.jingle.thincapture.ui.ThinCapturePluginPanel;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ThinCapture {
    public static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static ThinCaptureOptions options = null;

    // Thin BT frames
    private static final List<CaptureFrame> frames = new ArrayList<>();
    private static final List<BackgroundFrame> bgFrames = new ArrayList<>();
    private static boolean thinBTShowing = false;

    // Planar Abuse frames
    private static final List<CaptureFrame> planarFrames = new ArrayList<>();
    private static final List<BackgroundFrame> planarBgFrames = new ArrayList<>();
    private static boolean planarShowing = false;

    // EyeSee frames
    private static final List<BackgroundFrame> eyeSeeBgFrames = new ArrayList<>();

    public static ThinCaptureOptions getOptions() {
        return options;
    }

    public static void main(String[] args) throws IOException {
        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(ThinCapture.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), ThinCapture::initialize);
    }

    public static void initialize() {
        Optional<ThinCaptureOptions> loaded = ThinCaptureOptions.load();
        if (loaded.isPresent()) {
            options = loaded.get();
        } else {
            options = new ThinCaptureOptions();
            Jingle.log(Level.ERROR, "Failed to load ThinCapture options, using defaults.");
        }

        // Initialize Thin BT frames
        for (CaptureConfig config : options.captures) {
            frames.add(new CaptureFrame(config.name));
        }
        for (BackgroundConfig bg : options.backgrounds) {
            BackgroundFrame frame = new BackgroundFrame();
            if (bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                frame.loadImage(bg.imagePath);
            }
            bgFrames.add(frame);
        }

        // Initialize Planar Abuse frames
        for (CaptureConfig config : options.planarAbuseCaptures) {
            planarFrames.add(new CaptureFrame(config.name));
        }
        for (BackgroundConfig bg : options.planarAbuseBackgrounds) {
            BackgroundFrame frame = new BackgroundFrame();
            if (bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                frame.loadImage(bg.imagePath);
            }
            planarBgFrames.add(frame);
        }

        // Initialize EyeSee frames
        for (BackgroundConfig bg : options.eyeSeeBackgrounds) {
            BackgroundFrame frame = new BackgroundFrame();
            if (bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                frame.loadImage(bg.imagePath);
            }
            eyeSeeBgFrames.add(frame);
        }

        // Add plugin tabs
        ThinCapturePluginPanel thinPanel = new ThinCapturePluginPanel();
        JingleGUI.addPluginTab("Thin Captures", thinPanel.mainPanel, thinPanel::onSwitchTo);

        PlanarAbusePluginPanel planarPanel = new PlanarAbusePluginPanel();
        JingleGUI.addPluginTab("Wide Captures", planarPanel.mainPanel, planarPanel::onSwitchTo);

        BackgroundsPluginPanel bgPanel = new BackgroundsPluginPanel();
        JingleGUI.addPluginTab("Backgrounds", bgPanel.mainPanel, bgPanel::onSwitchTo);

        // Register events
        PluginEvents.START_TICK.register(ThinCapture::detectResize);
        PluginEvents.SHOW_PROJECTOR.register(ThinCapture::showEyeSeeCaptures);
        PluginEvents.DUMP_PROJECTOR.register(ThinCapture::hideEyeSeeCaptures);
        PluginEvents.STOP.register(ThinCapture::stop);

        Jingle.log(Level.INFO, "ThinCapture Plugin Initialized (" +
                options.captures.size() + " thin captures, " +
                options.planarAbuseCaptures.size() + " planar captures, " +
                options.eyeSeeBackgrounds.size() + " eyesee backgrounds)");
    }

    // ===== Thin BT Methods =====

    public static void addCapture(String name) {
        options.captures.add(new CaptureConfig(name));
        frames.add(new CaptureFrame(name));
    }

    public static void removeCapture(int index) {
        if (index < 0 || index >= options.captures.size()) return;
        options.captures.remove(index);
        CaptureFrame frame = frames.remove(index);
        if (frame.isShowing()) frame.hideCapture();
        frame.dispose();
    }

    public static void renameCapture(int index, String newName) {
        if (index < 0 || index >= options.captures.size()) return;
        options.captures.get(index).name = newName;
        frames.get(index).setTitle("ThinCapture " + newName);
    }

    public static void addBackground(String name) {
        BackgroundConfig config = new BackgroundConfig(name);
        config.enabled = true;
        options.backgrounds.add(config);
        bgFrames.add(new BackgroundFrame());
    }

    public static void removeBackground(int index) {
        if (index < 0 || index >= options.backgrounds.size()) return;
        options.backgrounds.remove(index);
        BackgroundFrame frame = bgFrames.remove(index);
        if (frame.isShowing()) frame.hideBackground();
        frame.dispose();
    }

    public static void renameBackground(int index, String newName) {
        if (index < 0 || index >= options.backgrounds.size()) return;
        options.backgrounds.get(index).name = newName;
    }

    public static BackgroundFrame getBgFrame(int index) {
        if (index < 0 || index >= bgFrames.size()) return null;
        return bgFrames.get(index);
    }

    // ===== Planar Abuse Methods =====

    public static void addPlanarCapture(String name) {
        options.planarAbuseCaptures.add(new CaptureConfig(name));
        planarFrames.add(new CaptureFrame(name));
    }

    public static void removePlanarCapture(int index) {
        if (index < 0 || index >= options.planarAbuseCaptures.size()) return;
        options.planarAbuseCaptures.remove(index);
        CaptureFrame frame = planarFrames.remove(index);
        if (frame.isShowing()) frame.hideCapture();
        frame.dispose();
    }

    public static void renamePlanarCapture(int index, String newName) {
        if (index < 0 || index >= options.planarAbuseCaptures.size()) return;
        options.planarAbuseCaptures.get(index).name = newName;
        planarFrames.get(index).setTitle("ThinCapture " + newName);
    }

    public static void addPlanarBackground(String name) {
        BackgroundConfig config = new BackgroundConfig(name);
        config.enabled = true;
        options.planarAbuseBackgrounds.add(config);
        planarBgFrames.add(new BackgroundFrame());
    }

    public static void removePlanarBackground(int index) {
        if (index < 0 || index >= options.planarAbuseBackgrounds.size()) return;
        options.planarAbuseBackgrounds.remove(index);
        BackgroundFrame frame = planarBgFrames.remove(index);
        if (frame.isShowing()) frame.hideBackground();
        frame.dispose();
    }

    public static void renamePlanarBackground(int index, String newName) {
        if (index < 0 || index >= options.planarAbuseBackgrounds.size()) return;
        options.planarAbuseBackgrounds.get(index).name = newName;
    }

    public static BackgroundFrame getPlanarBgFrame(int index) {
        if (index < 0 || index >= planarBgFrames.size()) return null;
        return planarBgFrames.get(index);
    }

    // ===== EyeSee Methods =====

    public static void addEyeSeeBackground(String name) {
        BackgroundConfig config = new BackgroundConfig(name);
        config.enabled = true;
        options.eyeSeeBackgrounds.add(config);
        eyeSeeBgFrames.add(new BackgroundFrame());
    }

    public static void removeEyeSeeBackground(int index) {
        if (index < 0 || index >= options.eyeSeeBackgrounds.size()) return;
        options.eyeSeeBackgrounds.remove(index);
        BackgroundFrame frame = eyeSeeBgFrames.remove(index);
        if (frame.isShowing()) frame.hideBackground();
        frame.dispose();
    }

    public static void renameEyeSeeBackground(int index, String newName) {
        if (index < 0 || index >= options.eyeSeeBackgrounds.size()) return;
        options.eyeSeeBackgrounds.get(index).name = newName;
    }

    public static BackgroundFrame getEyeSeeBgFrame(int index) {
        if (index < 0 || index >= eyeSeeBgFrames.size()) return null;
        return eyeSeeBgFrames.get(index);
    }

    // ===== Resize Detection =====

    private static void detectResize() {
        try {
            if (!Jingle.getMainInstance().isPresent()) {
                if (thinBTShowing) hideThinBTCaptures();
                if (planarShowing) hidePlanarCaptures();
                return;
            }

            WinDef.HWND hwnd = Jingle.getMainInstance().get().hwnd;
            WinDef.RECT rect = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hwnd, rect);
            int w = rect.right - rect.left;
            int h = rect.bottom - rect.top;

            boolean isThinBT = (w == options.thinBTWidth && h == options.thinBTHeight);
            boolean isPlanar = (w == options.planarAbuseWidth && h == options.planarAbuseHeight);

            // Thin BT
            if (isThinBT && !thinBTShowing) {
                showThinBTCaptures();
            } else if (!isThinBT && thinBTShowing) {
                hideThinBTCaptures();
            } else if (isThinBT) {
                for (BackgroundFrame bf : bgFrames) {
                    if (bf.isShowing()) bf.sendBehindMC();
                }
            }

            // Planar Abuse
            if (isPlanar && !planarShowing) {
                showPlanarCaptures();
            } else if (!isPlanar && planarShowing) {
                hidePlanarCaptures();
            } else if (isPlanar) {
                for (BackgroundFrame bf : planarBgFrames) {
                    if (bf.isShowing()) bf.sendBehindMC();
                }
            }
        } catch (Exception ignored) {}
    }

    // ===== Thin BT Show/Hide =====

    private static void showThinBTCaptures() {
        thinBTShowing = true;

        for (int i = 0; i < options.backgrounds.size() && i < bgFrames.size(); i++) {
            BackgroundConfig bg = options.backgrounds.get(i);
            BackgroundFrame bf = bgFrames.get(i);
            if (bg.enabled && bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                bf.positionBackground(bg.x, bg.y, bg.width, bg.height);
            }
        }

        List<CaptureFrame> toShow = new ArrayList<>();
        for (int i = 0; i < options.captures.size() && i < frames.size(); i++) {
            CaptureConfig c = options.captures.get(i);
            CaptureFrame f = frames.get(i);
            if (c.enabled) {
                f.setFilterOptions(c.textOnly, c.textThreshold, c.transparentBg, parseColor(c.bgColor), c.bgImagePath);
                f.positionCapture(
                        new Rectangle(c.screenX, c.screenY, c.screenW, c.screenH),
                        new Rectangle(c.captureX, c.captureY, c.captureW, c.captureH)
                );
                toShow.add(f);
            }
        }

        for (int i = 0; i < options.backgrounds.size() && i < bgFrames.size(); i++) {
            BackgroundConfig bg = options.backgrounds.get(i);
            BackgroundFrame bf = bgFrames.get(i);
            if (bg.enabled && bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                bf.showBackground();
            }
        }
        for (CaptureFrame f : toShow) {
            f.showCapture();
        }
    }

    private static void hideThinBTCaptures() {
        thinBTShowing = false;
        for (BackgroundFrame bf : bgFrames) {
            if (bf.isShowing()) bf.hideBackground();
        }
        for (CaptureFrame f : frames) {
            if (f.isShowing()) f.hideCapture();
        }
    }

    // ===== Planar Abuse Show/Hide =====

    private static void showPlanarCaptures() {
        planarShowing = true;

        for (int i = 0; i < options.planarAbuseBackgrounds.size() && i < planarBgFrames.size(); i++) {
            BackgroundConfig bg = options.planarAbuseBackgrounds.get(i);
            BackgroundFrame bf = planarBgFrames.get(i);
            if (bg.enabled && bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                bf.positionBackground(bg.x, bg.y, bg.width, bg.height);
            }
        }

        List<CaptureFrame> toShow = new ArrayList<>();
        for (int i = 0; i < options.planarAbuseCaptures.size() && i < planarFrames.size(); i++) {
            CaptureConfig c = options.planarAbuseCaptures.get(i);
            CaptureFrame f = planarFrames.get(i);
            if (c.enabled) {
                f.setFilterOptions(c.textOnly, c.textThreshold, c.transparentBg, parseColor(c.bgColor), c.bgImagePath);
                f.positionCapture(
                        new Rectangle(c.screenX, c.screenY, c.screenW, c.screenH),
                        new Rectangle(c.captureX, c.captureY, c.captureW, c.captureH)
                );
                toShow.add(f);
            }
        }

        for (int i = 0; i < options.planarAbuseBackgrounds.size() && i < planarBgFrames.size(); i++) {
            BackgroundConfig bg = options.planarAbuseBackgrounds.get(i);
            BackgroundFrame bf = planarBgFrames.get(i);
            if (bg.enabled && bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                bf.showBackground();
            }
        }
        for (CaptureFrame f : toShow) {
            f.showCapture();
        }
    }

    private static void hidePlanarCaptures() {
        planarShowing = false;
        for (BackgroundFrame bf : planarBgFrames) {
            if (bf.isShowing()) bf.hideBackground();
        }
        for (CaptureFrame f : planarFrames) {
            if (f.isShowing()) f.hideCapture();
        }
    }

    // ===== EyeSee Show/Hide =====

    private static void showEyeSeeCaptures() {
        if (!options.eyeSeeEnabled) return;
        if (!Jingle.getMainInstance().isPresent()) return;

        for (int i = 0; i < options.eyeSeeBackgrounds.size() && i < eyeSeeBgFrames.size(); i++) {
            BackgroundConfig bg = options.eyeSeeBackgrounds.get(i);
            BackgroundFrame bf = eyeSeeBgFrames.get(i);
            if (bg.enabled && bg.imagePath != null && !bg.imagePath.trim().isEmpty()) {
                bf.positionBackground(bg.x, bg.y, bg.width, bg.height);
                bf.showBackground();
            }
        }
    }

    private static void hideEyeSeeCaptures() {
        if (!options.eyeSeeEnabled) return;

        for (BackgroundFrame bf : eyeSeeBgFrames) {
            if (bf.isShowing()) bf.hideBackground();
        }
    }

    // ===== Utilities =====

    private static Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    public static void updateFpsLimit() {
        int fps = options.fpsLimit;
        for (CaptureFrame f : frames) {
            f.restartDrawingTask(fps);
        }
        int planarFps = options.planarAbuseFpsLimit;
        for (CaptureFrame f : planarFrames) {
            f.restartDrawingTask(planarFps);
        }
    }

    private static void stop() {
        EXECUTOR.shutdown();
        for (CaptureFrame f : frames) f.dispose();
        for (BackgroundFrame bf : bgFrames) bf.dispose();
        for (CaptureFrame f : planarFrames) f.dispose();
        for (BackgroundFrame bf : planarBgFrames) bf.dispose();
        for (BackgroundFrame bf : eyeSeeBgFrames) bf.dispose();
        if (options != null) if (!options.trySave()) Jingle.log(Level.ERROR, "Failed to save ThinCapture options!");
    }
}