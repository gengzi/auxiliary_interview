package com.gengzi.desktop.hotkey;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;

import java.awt.*;

/**
 * 全局快捷键实现
 * 使用 JIntellitype 库 (Windows专用)
 */
public class SystemHotkey {
    // 定义快捷键唯一标识符
    private static final int HOTKEY_ID = 1;

    private final HotkeyCallback callback;
    private volatile boolean registered = false;

    public interface HotkeyCallback {
        void onHotkeyPressed();
    }

    public SystemHotkey(HotkeyCallback callback) {
        this.callback = callback;
    }

    /**
     * 注册 Ctrl+P 全局快捷键
     * @param parent 父窗口(可以为null)
     * @return 是否成功
     */
    public boolean register(Window parent) {
        if (registered) {
            System.out.println("快捷键已经注册");
            return true;
        }

        try {
            System.out.println("开始注册全局快捷键 Ctrl+P...");

            // 检查 JIntellitype 是否可用
            if (!JIntellitype.isJIntellitypeSupported()) {
                System.err.println("JIntellitype 不支持此平台 (仅支持 Windows)");
                return false;
            }

            // 初始化 JIntellitype
            JIntellitype.getInstance().addHotKeyListener(new HotkeyListener() {
                @Override
                public void onHotKey(int identifier) {
                    System.out.println("全局快捷键被触发, ID: " + identifier);
                    if (identifier == HOTKEY_ID) {
                        System.out.println("触发 Ctrl+P 回调");
                        if (callback != null) {
                            try {
                                callback.onHotkeyPressed();
                            } catch (Exception e) {
                                System.err.println("热键回调出错: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            // 注册 Ctrl+P 快捷键
            // MOD_CONTROL = 2, VK_P = 0x50
            JIntellitype.getInstance().registerHotKey(
                HOTKEY_ID,
                JIntellitype.MOD_CONTROL,
                java.awt.event.KeyEvent.VK_P
            );

            registered = true;
            System.out.println("✓ 全局快捷键 Ctrl+P 注册成功 (使用 JIntellitype)");
            System.out.println("现在可以随时按 Ctrl+P 触发截图");

            return true;

        } catch (Exception e) {
            System.err.println("✗ 全局快捷键注册失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 取消注册
     */
    public void unregister() {
        if (registered) {
            try {
                JIntellitype.getInstance().unregisterHotKey(HOTKEY_ID);
                JIntellitype.getInstance().cleanUp();
                System.out.println("全局快捷键已取消注册");
            } catch (Exception e) {
                System.err.println("取消注册时出错: " + e.getMessage());
            }
        }

        registered = false;
    }
}
