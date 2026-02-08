package xyz.vibzz.jingle.thincapture.win32;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface User32Extra extends StdCallLibrary {
    User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean ClientToScreen(WinDef.HWND hWnd, WinDef.POINT lpPoint);
}