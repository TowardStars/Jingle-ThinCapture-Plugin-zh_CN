package xyz.vibzz.jingle.thincapture.util;

import com.sun.jna.platform.win32.WinDef;
import xyz.vibzz.jingle.thincapture.win32.GDI32Extra;
import xyz.duncanruns.jingle.win32.User32;

public final class ScaleUtil {
    private static float scaleFactor = -1f;

    private ScaleUtil() {
    }

    public static float getScaleFactor() {
        if (scaleFactor < 0) {
            if (System.getProperty("java.version").startsWith("1.8")) {
                try {
                    WinDef.HDC hdc = User32.INSTANCE.GetWindowDC(null);
                    int virtualHeight = GDI32Extra.INSTANCE.GetDeviceCaps(hdc, 10);   // VERTRES
                    int physicalHeight = GDI32Extra.INSTANCE.GetDeviceCaps(hdc, 117); // DESKTOPVERTRES
                    User32.INSTANCE.ReleaseDC(null, hdc);
                    scaleFactor = Math.round((float) physicalHeight / (float) virtualHeight * 100f) / 100f;
                } catch (Exception e) {
                    scaleFactor = 1.0f;
                }
            } else {
                scaleFactor = 1.0f;
            }
        }
        return scaleFactor;
    }
}