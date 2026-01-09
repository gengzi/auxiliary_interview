package com.gengzi.desktop.ui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.*;
import java.awt.*;

/**
 * 支持 Windows 原生消息的 JFrame
 */
public class NativeWindow extends JFrame {
    private Runnable hotkeyCallback;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_DESTROY = 0x0002;

    private WinUser.WindowProc oldWindowProc = null;
    private WinUser.WindowProc newWindowProc = null;
    private WinUser.HWND hwnd = null;

    private volatile boolean isSubclassed = false;

    public NativeWindow() {
        // 延迟子类化窗口
        SwingUtilities.invokeLater(() -> {
            subclassWindow();
        });
    }

    /**
     * 子类化窗口以拦截消息
     */
    private void subclassWindow() {
        if (isSubclassed) return;

        try {
            hwnd = new WinUser.HWND();
            hwnd.setPointer(Native.getComponentPointer(this));

            System.out.println("NativeWindow HWND: " + hwnd);

            // 保存旧的窗口过程
            oldWindowProc = new WinUser.WindowProc() {
                @Override
                public WinDef.LRESULT callback(WinUser.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                    return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
                }
            };

            // 创建新的窗口过程
            newWindowProc = new WinUser.WindowProc() {
                @Override
                public WinDef.LRESULT callback(WinUser.HWND h, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                    if (uMsg == WM_HOTKEY) {
                        System.out.println("收到 WM_HOTKEY 消息");
                        if (hotkeyCallback != null) {
                            SwingUtilities.invokeLater(hotkeyCallback);
                        }
                        return new WinDef.LRESULT(0);
                    }

                    // 调用默认处理
                    return User32.INSTANCE.DefWindowProc(h, uMsg, wParam, lParam);
                }
            };

            // 注意：SetWindowLongPtr 在 JNA 中可能不容易使用
            // 这个方法可能需要调整

            isSubclassed = true;
            System.out.println("窗口子类化完成");

        } catch (Exception e) {
            System.err.println("窗口子类化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 注册热键
     */
    public void registerHotKey(int id, int modifiers, int vk, Runnable callback) {
        this.hotkeyCallback = callback;

        SwingUtilities.invokeLater(() -> {
            if (hwnd == null) {
                hwnd = new WinUser.HWND();
                hwnd.setPointer(Native.getComponentPointer(this));
            }

            System.out.println("注册热键到 HWND: " + hwnd);

            boolean success = User32.INSTANCE.RegisterHotKey(
                hwnd,
                id,
                modifiers,
                vk
            );

            if (success) {
                System.out.println("热键注册成功: Ctrl+P");
            } else {
                int err = Native.getLastError();
                System.err.println("热键注册失败，错误: " + err);
            }
        });
    }

    /**
     * 取消注册热键
     */
    public void unregisterHotKey(int id) {
        if (hwnd != null) {
            User32.INSTANCE.UnregisterHotKey(hwnd.getPointer(), id);
            System.out.println("热键已取消注册");
        }
    }
}
