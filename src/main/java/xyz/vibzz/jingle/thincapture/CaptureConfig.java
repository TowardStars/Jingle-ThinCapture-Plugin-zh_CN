package xyz.vibzz.jingle.thincapture;

public class CaptureConfig {
    public String name = "New Capture";
    public boolean enabled = false;

    // Overlay position on monitor
    public int screenX = 0;
    public int screenY = 0;
    public int screenW = 200;
    public int screenH = 200;

    // MC window region to capture
    public int captureX = 0;
    public int captureY = 0;
    public int captureW = 200;
    public int captureH = 200;

    // Filtering
    public boolean textOnly = false;
    public int textThreshold = 200;
    public boolean transparentBg = true;
    public String bgColor = "#000000";

    public CaptureConfig() {}

    public CaptureConfig(String name) {
        this.name = name;
    }
}