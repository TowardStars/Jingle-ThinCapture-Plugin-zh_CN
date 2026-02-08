package xyz.vibzz.jingle.thincapture.win32;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.win32.W32APIOptions;

public interface GDI32Extra extends xyz.duncanruns.jingle.win32.GDI32Extra {
    GDI32Extra INSTANCE = Native.load("gdi32", GDI32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

    int GetDeviceCaps(HDC hdc, int index);
}