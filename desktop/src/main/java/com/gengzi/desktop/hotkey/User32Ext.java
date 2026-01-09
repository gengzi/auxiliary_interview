package com.gengzi.desktop.hotkey;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

/**
 * 扩展的 User32 接口
 * 包含所有热键相关的 Windows API 方法
 */
public interface User32Ext extends StdCallLibrary {

    User32Ext INSTANCE = Native.load("user32", User32Ext.class);

    // 窗口类相关
    short RegisterClassExA(WNDCLASSEX lpwcx);

    boolean UnregisterClassA(String lpClassName, HINSTANCE hInstance);

    // 窗口创建相关
    HWND CreateWindowExA(
        int dwExStyle,
        String lpClassName,
        String lpWindowName,
        int dwStyle,
        int x, int y,
        int nWidth, int nHeight,
        HWND hWndParent,
        HMENU hMenu,
        HINSTANCE hInstance,
        Pointer lpParam
    );

    boolean DestroyWindow(HWND hWnd);

    boolean ShowWindow(HWND hWnd, int nCmdShow);

    boolean UpdateWindow(HWND hWnd);

    // 消息相关
    boolean GetMessageA(
        MSG lpMsg,
        HWND hWnd,
        int wMsgFilterMin,
        int wMsgFilterMax
    );

    boolean PeekMessageA(
        MSG lpMsg,
        HWND hWnd,
        int wMsgFilterMin,
        int wMsgFilterMax,
        int wRemoveMsg
    );

    boolean TranslateMessage(MSG lpMsg);

    LRESULT DispatchMessageA(MSG lpMsg);

    boolean PostMessage(HWND hWnd, int Msg, WPARAM wParam, LPARAM lParam);

    // 热键相关
    boolean RegisterHotKey(
        HWND hWnd,
        int id,
        int fsModifiers,
        int vk
    );

    boolean UnregisterHotKey(HWND hWnd, int id);

    // 默认窗口过程
    LRESULT DefWindowProcA(
        HWND hWnd,
        int Msg,
        WPARAM wParam,
        LPARAM lParam
    );

    // 常量
    int SW_HIDE = 0;
    int SW_SHOW = 5;
    int PM_REMOVE = 0x0001;
    int PM_NOREMOVE = 0x0000;
    int WM_HOTKEY = 0x0312;
    int WM_QUIT = 0x0012;
    int MOD_ALT = 0x0001;
    int MOD_CONTROL = 0x0002;
    int MOD_SHIFT = 0x0004;
    int MOD_WIN = 0x0008;
    int WS_OVERLAPPEDWINDOW = 0x00CF0000;
}
