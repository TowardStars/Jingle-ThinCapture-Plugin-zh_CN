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

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ThinCapture {
    public static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static ThinCaptureOptions options = null;
    private static final List<CaptureFrame> frames = new ArrayList<>();
    private static boolean capturesShowing = false;
    private static ThinCapturePluginPanel pluginPanel = null;

    public static ThinCaptureOptions getOptions() {
        return options;
    }

    public static List<CaptureFrame> getFrames() {
        return frames;
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

        // Create frames for existing configs
        for (CaptureConfig config : options.captures) {
            frames.add(new CaptureFrame(config.name));
        }

        pluginPanel = new ThinCapturePluginPanel();
        JingleGUI.addPluginTab("ThinCapture", pluginPanel.mainPanel, pluginPanel::onSwitchTo);

        PluginEvents.STOP.register(ThinCapture::stop);

        EXECUTOR.scheduleAtFixedRate(ThinCapture::detectThinBT, 500, 200, TimeUnit.MILLISECONDS);

        Jingle.log(Level.INFO, "ThinCapture Plugin Initialized (" + options.captures.size() + " captures)");
    }

    /**
     * Adds a new capture config and frame.
     */
    public static CaptureConfig addCapture(String name) {
        CaptureConfig config = new CaptureConfig(name);
        options.captures.add(config);
        CaptureFrame frame = new CaptureFrame(name);
        frames.add(frame);
        return config;
    }

    /**
     * Removes a capture config and frame by index.
     */
    public static void removeCapture(int index) {
        if (index < 0 || index >= options.captures.size()) return;
        options.captures.remove(index);
        CaptureFrame frame = frames.remove(index);
        if (frame.isShowing()) frame.hideCapture();
        frame.dispose();
    }

    /**
     * Renames a capture and its frame.
     */
    public static void renameCapture(int index, String newName) {
        if (index < 0 || index >= options.captures.size()) return;
        options.captures.get(index).name = newName;
        frames.get(index).setTitle("ThinCapture " + newName);
    }

    private static void detectThinBT() {
        try {
            if (!Jingle.getMainInstance().isPresent()) {
                if (capturesShowing) hideCaptureWindows();
                return;
            }

            WinDef.HWND hwnd = Jingle.getMainInstance().get().hwnd;
            WinDef.RECT rect = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hwnd, rect);
            int w = rect.right - rect.left;
            int h = rect.bottom - rect.top;

            boolean isThinBT = (w == options.thinBTWidth && h == options.thinBTHeight);

            if (isThinBT && !capturesShowing) {
                showCaptureWindows();
            } else if (!isThinBT && capturesShowing) {
                hideCaptureWindows();
            }
        } catch (Exception ignored) {
        }
    }

    private static void showCaptureWindows() {
        capturesShowing = true;
        for (int i = 0; i < options.captures.size() && i < frames.size(); i++) {
            CaptureConfig c = options.captures.get(i);
            CaptureFrame f = frames.get(i);
            if (c.enabled) {
                f.setFilterOptions(c.textOnly, c.textThreshold, c.transparentBg, parseColor(c.bgColor));
                f.showCapture(
                        new Rectangle(c.screenX, c.screenY, c.screenW, c.screenH),
                        new Rectangle(c.captureX, c.captureY, c.captureW, c.captureH)
                );
            }
        }
    }

    private static void hideCaptureWindows() {
        capturesShowing = false;
        for (CaptureFrame f : frames) {
            if (f.isShowing()) f.hideCapture();
        }
    }

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
    }

    private static void stop() {
        EXECUTOR.shutdown();
        for (CaptureFrame f : frames) {
            f.dispose();
        }
        if (options != null) if (!options.trySave()) Jingle.log(Level.ERROR, "Failed to save ThinCapture options!");
    }
}