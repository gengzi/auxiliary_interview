package com.gengzi.desktop.hotkey;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.*;

/**
 * 全局快捷键管理器（Windows）
 * 使用 JNA 的 WindowsHook 来监听全局快捷键
 */
public class GlobalHotkey {
    private static final int WM_HOTKEY = 0x0312;

    private final HotkeyCallback callback;
    private WinUser.HHOOK hookHandle = null;

    public interface HotkeyCallback {
        void onHotkeyPressed();
    }

    public GlobalHotkey(HotkeyCallback callback) {
        this.callback = callback;
    }

    /**
     * 注册 Ctrl+P 全局快捷键
     * 使用 Windows 消息钩子
     */
    public void registerCtrlP(Window window) {
        System.out.println("注册全局快捷键 Ctrl+P (使用消息钩子)...");

        // 获取窗口 HWND
        WinUser.HWND hwnd = new WinUser.HWND();
        hwnd.setPointer(Native.getComponentPointer(window));

        // 注册热键
        int hotkeyId = 1;
        boolean success = User32.INSTANCE.RegisterHotKey(
            hwnd,
            hotkeyId,
            0x0002,  // MOD_CONTROL
            0x50     // VK_P
        );

        if (success) {
            System.out.println("快捷键注册成功 (ID: " + hotkeyId + ")");
        } else {
            int err = Native.getLastError();
            System.err.println("快捷键注册失败，错误代码: " + err);
            if (err == 1409) { // ERROR_HOTKEY_ALREADY_REGISTERED
                System.err.println("快捷键已被其他程序占用");
            }
        }
    }

    /**
     * 取消注册全局快捷键
     */
    public void unregister() {
        // RegisterHotKey 会随窗口自动清理，无需手动取消
        System.out.println("快捷键将随窗口自动清理");
    }
}
