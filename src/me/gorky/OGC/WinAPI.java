package me.gorky.OGC;

import com.sun.jna.Native;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
//import com.sun.jna.platform.win32.Kernel32;
//import com.sun.jna.platform.win32.WinNT.HANDLE;
//import com.sun.jna.ptr.IntByReference;

import java.util.Set;

public class WinAPI {

    private static HWND foregroundWindow;

    public static boolean windowIsInFocus(Set<String> titles) {
        foregroundWindow = User32.INSTANCE.GetForegroundWindow();
        String title = getWindowTitle(foregroundWindow);

        WinUser.WINDOWINFO info = new WinUser.WINDOWINFO();
        User32.INSTANCE.GetWindowInfo(foregroundWindow, info);
        boolean minimized = (info.dwStyle & WinUser.WS_ICONIC) == WinUser.WS_ICONIC;

        return !minimized && titles.contains(title);
    }

    public static void minimizeWindow() {
        User32.INSTANCE.ShowWindow(foregroundWindow, WinUser.SW_MINIMIZE);
    }

    // Neither of this shit works properly for some reason, so I'm forced to use minimization:
    /*
    public static void killProcess() {
        IntByReference pointer = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(foregroundWindow, pointer);
        HANDLE pHandle = Kernel32.INSTANCE.OpenProcess(Kernel32.PROCESS_TERMINATE, false, pointer.getValue());

        if (pHandle != null) {
            Kernel32.INSTANCE.TerminateProcess(pHandle, 9);
            Kernel32.INSTANCE.CloseHandle(pHandle);
        }
    }

    public static void closeWindow() {
        User32.INSTANCE.PostMessage(foregroundWindow, WinUser.WM_QUIT, null, null);
    }
    */

    private static String getWindowTitle(HWND window) {
        char[] title = new char[512];
        User32.INSTANCE.GetWindowText(window, title, 512);

        return Native.toString(title);
    }

}
