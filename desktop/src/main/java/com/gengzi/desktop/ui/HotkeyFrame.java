package com.gengzi.desktop.ui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.*;
import java.awt.*;

/**
 * 支持全局快捷键的 JFrame
 */
public class HotkeyFrame extends JFrame {
    private static final int WM_HOTKEY = 0x0312;

    private volatile boolean isProcessing = false;
    private Runnable hotkeyCallback;

    public HotkeyFrame() {
        // 启用本地窗口
        try {
            this.getToolkit().getSystemEventQueue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册全局快捷键
     */
    public void registerGlobalHotKey(int id, int modifiers, int keycode, Runnable callback) {
        this.hotkeyCallback = callback;

        // 等待窗口显示后再注册
        SwingUtilities.invokeLater(() -> {
            WinUser.HWND hwnd = new WinUser.HWND();
            hwnd.setPointer(Native.getComponentPointer(this));

            System.out.println("注册快捷键，HWND: " + hwnd);

            boolean success = User32.INSTANCE.RegisterHotKey(
                hwnd,
                id,
                modifiers,
                keycode
            );

            if (success) {
                System.out.println("快捷键注册成功，ID: " + id);
            } else {
                System.err.println("快捷键注册失败，错误: " + Native.getLastError());
            }
        });
    }

    /**
     * 取消注册全局快捷键
     */
    public void unregisterGlobalHotKey(int id) {
        WinUser.HWND hwnd = new WinUser.HWND();
        hwnd.setPointer(Native.getComponentPointer(this));

        boolean success = User32.INSTANCE.UnregisterHotKey(hwnd.getPointer(), id);
        if (success) {
            System.out.println("快捷键取消注册成功");
        }
    }

    @Override
    protected void processEvent(java.awt.AWTEvent event) {
        if (event instanceof java.awt.event.InputEvent) {
            // 检查是否是热键消息
            Object nativeSource = ((java.awt.event.InputEvent) event).getSource();
        }
        super.processEvent(event);
    }

    /**
     * 处理 Windows 消息
     */
    public boolean processWindowsMessage(int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        if (msg == WM_HOTKEY) {
            System.out.println("收到 WM_HOTKEY 消息");
            if (hotkeyCallback != null && !isProcessing) {
                new Thread(() -> {
                    isProcessing = true;
                    try {
                        hotkeyCallback.run();
                    } finally {
                        isProcessing = false;
                    }
                }).start();
            }
            return true;
        }
        return false;
    }
}
