package com.gengzi.desktop.hotkey;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.*;

/**
 * 真正的全局快捷键（系统级）
 * 使用 Windows 的 RegisterHotKey API
 * 即使窗口最小化或失去焦点也能工作
 */
public class SystemHotkey {
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_ALT = 0x0001;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_WIN = 0x0008;

    private final HotkeyCallback callback;
    private int hotkeyId;
    private MessageLoopThread messageThread;
    private volatile boolean registered = false;

    public interface HotkeyCallback {
        void onHotkeyPressed();
    }

    public SystemHotkey(HotkeyCallback callback) {
        this.callback = callback;
    }

    /**
     * 注册 Ctrl+P 全局快捷键
     * @param parent 父窗口
     * @return 是否成功
     */
    public boolean register(Window parent) {
        if (registered) {
            System.out.println("快捷键已经注册");
            return true;
        }

        System.out.println("开始注册系统级全局快捷键 Ctrl+P...");

        // 启动消息循环线程
        messageThread = new MessageLoopThread(parent);
        messageThread.start();

        // 等待消息循环准备好
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 在消息循环线程中注册热键
        hotkeyId = messageThread.registerHotKey(MOD_CONTROL, 0x50); // Ctrl+P

        if (hotkeyId > 0) {
            registered = true;
            System.out.println("✓ 全局快捷键 Ctrl+P 注册成功！");
            System.out.println("  现在可以随时按 Ctrl+P 触发截图");
            System.out.println("  即使窗口最小化也有效");
            return true;
        } else {
            System.err.println("✗ 全局快捷键注册失败");
            return false;
        }
    }

    /**
     * 取消注册
     */
    public void unregister() {
        if (messageThread != null) {
            messageThread.stopLoop();
            messageThread = null;
        }
        registered = false;
        System.out.println("全局快捷键已取消注册");
    }

    /**
     * 消息循环线程
     * 专门用于处理 Windows 消息和热键
     */
    private class MessageLoopThread extends Thread {
        private final Window parent;
        private volatile boolean running = false;
        private WinUser.HWND msgHwnd;

        public MessageLoopThread(Window parent) {
            super("Hotkey-MessageLoop");
            this.parent = parent;
            setDaemon(false); // 不能是守护线程
        }

        /**
         * 注册热键
         */
        public int registerHotKey(int modifiers, int vk) {
            // 等待消息循环准备好
            int attempts = 0;
            while (msgHwnd == null && attempts < 100) {
                try {
                    Thread.sleep(100);
                    attempts++;
                } catch (InterruptedException e) {
                    return -1;
                }
            }

            if (msgHwnd == null) {
                System.err.println("消息循环未准备好，等待超时");
                return -1;
            }

            System.out.println("消息窗口已准备好: " + msgHwnd);

            // 尝试注册热键
            for (int id = 1; id <= 10; id++) {
                System.out.println("尝试注册热键，ID: " + id + ", HWND: " + msgHwnd);

                boolean success = User32.INSTANCE.RegisterHotKey(
                    msgHwnd,
                    id,
                    modifiers,
                    vk
                );

                if (success) {
                    System.out.println("✓ 热键注册成功，ID: " + id);
                    return id;
                } else {
                    int err = Native.getLastError();
                    System.err.println("✗ ID " + id + " 注册失败，错误: " + err +
                        (err == 1408 ? " (窗口句柄无效或无消息循环)" : ""));
                }
            }

            return -1;
        }

        @Override
        public void run() {
            running = true;

            // 创建隐藏窗口
            System.out.println("正在创建消息窗口...");
            msgHwnd = createMessageWindow();

            if (msgHwnd == null) {
                System.err.println("创建消息窗口失败");
                return;
            }

            System.out.println("消息窗口已创建，HWND: " + msgHwnd);
            System.out.println("窗口句柄指针: " + msgHwnd.getPointer());

            // 等待一下，确保窗口完全初始化
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 进入消息循环
            System.out.println("进入消息循环...");
            WinUser.MSG msg = new WinUser.MSG();

            while (running) {
                // 使用 GetMessage 等待消息
                int ret = User32.INSTANCE.GetMessage(msg, null, 0, 0);

                if (ret == 0 || ret == -1) {
                    // WM_QUIT 或错误
                    System.out.println("GetMessage 返回 " + ret + "，退出消息循环");
                    break;
                }

                // 处理消息
                if (msg.message == WM_HOTKEY) {
                    System.out.println("收到热键消息！");
                    onHotkeyReceived();
                }

                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

            System.out.println("消息循环已退出");
        }

        /**
         * 创建消息窗口
         */
        private WinUser.HWND createMessageWindow() {
            // 定义窗口过程
            WinUser.WindowProc wndProc = new WinUser.WindowProc() {
                @Override
                public WinDef.LRESULT callback(WinUser.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                    if (uMsg == WM_HOTKEY) {
                        System.out.println("热键被触发 (窗口过程)");
                        onHotkeyReceived();
                        return new WinDef.LRESULT(0);
                    }
                    return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
                }
            };

            // 获取模块句柄
            WinDef.HMODULE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
            System.out.println("模块句柄: " + hInstance);

            // 注册窗口类
            String className = "HotkeyMessageWindow_" + System.currentTimeMillis();
            WinUser.WNDCLASSEX wc = new WinUser.WNDCLASSEX();
            wc.style = 0;
            wc.lpfnWndProc = wndProc;
            wc.cbClsExtra = 0;
            wc.cbWndExtra = 0;
            wc.hInstance = hInstance;
            wc.hIcon = null;
            wc.hCursor = null;
            wc.hbrBackground = null;
            wc.lpszMenuName = null;
            wc.lpszClassName = className;
            wc.hIconSm = null;

            int atom = User32.INSTANCE.RegisterClassEx(wc).intValue();
            if (atom == 0) {
                int err = Native.getLastError();
                if (err != 1410) { // 1410 = CLASS_ALREADY_EXISTS
                    System.err.println("注册窗口类失败，错误: " + err);
                } else {
                    System.out.println("窗口类已存在，使用现有类");
                }
            } else {
                System.out.println("窗口类注册成功，Atom: " + atom);
            }

            // 创建隐藏窗口 - 使用 WS_OVERLAPPEDWINDOW 确保有消息队列
            WinUser.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                0,                    // dwExStyle
                className,             // lpClassName
                "Hotkey Receiver",     // lpWindowName
                WinUser.WS_OVERLAPPEDWINDOW, // dwStyle (即使隐藏也要有这个标志)
                0, 0, 100, 100,        // x, y, width, height
                null,                  // hWndParent
                null,                  // hMenu
                hInstance,             // hInstance
                null                   // lpParam
            );

            if (hwnd == null) {
                System.err.println("创建窗口失败: " + Native.getLastError());
                return null;
            }

            System.out.println("窗口创建成功");

            // 确保窗口完全创建并初始化
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE);
            User32.INSTANCE.UpdateWindow(hwnd);

            System.out.println("窗口已初始化并隐藏");

            return hwnd;
        }

        /**
         * 热键被触发
         */
        private void onHotkeyReceived() {
            if (callback != null) {
                // 在新线程中执行，避免阻塞消息循环
                new Thread(() -> {
                    try {
                        callback.onHotkeyPressed();
                    } catch (Exception e) {
                        System.err.println("热键回调执行出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, "HotkeyCallback").start();
            }
        }

        /**
         * 停止消息循环
         */
        public void stopLoop() {
            running = false;
            if (msgHwnd != null) {
                User32.INSTANCE.PostMessage(msgHwnd, WinUser.WM_QUIT, null, null);
            }

            // 等待线程结束
            try {
                join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
