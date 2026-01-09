package com.gengzi.desktop.overlay;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.JWindow;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.Locale;

/**
 * 屏幕区域选择窗口
 * 核心功能：
 * 1. 全屏覆盖，允许用户通过鼠标拖拽选择屏幕区域
 * 2. 实时绘制选择矩形框（蓝色边框 + 透明背景）
 * 3. 使用CountDownLatch同步等待用户完成选择
 * 4. 选择完成后自动关闭并返回区域坐标
 */
public class SelectionWindow extends JWindow {
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private Point start;
    private Point end;
    private Rectangle selection;
    private final CountDownLatch latch = new CountDownLatch(1);

    /**
     * 初始化全屏选择窗口
     * 覆盖整个屏幕，设置半透明黑色背景，注册鼠标事件监听器
     */
    public SelectionWindow() {
        // 半透明黑色背景，略微变暗屏幕以便用户识别选择区域
        setBackground(new Color(0, 0, 0, 40));
        // 始终置顶显示
        setAlwaysOnTop(true);
        // 覆盖整个屏幕区域
        Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        setBounds(bounds);

        // 鼠标事件处理器：处理按下、拖拽、释放三个事件
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 记录鼠标按下位置作为起点
                start = e.getPoint();
                end = e.getPoint();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // 更新终点位置，实时重绘选择框
                end = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // 完成选择，记录最终区域
                end = e.getPoint();
                selection = buildRectangle(start, end);
                // 释放锁，通知等待线程
                latch.countDown();
                // 关闭窗口
                setVisible(false);
                dispose();
            }
        };

        // 注册鼠标监听器
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        // 启用鼠标事件
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    /**
     * 等待用户完成区域选择
     * 阻塞调用线程，直到用户释放鼠标
     * @return 用户选择的矩形区域，如果被中断返回null
     */
    public Rectangle waitForSelection() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return selection;
    }

    /**
     * 绘制选择框视觉效果
     * 选中区域清除背景色并绘制蓝色边框
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (start == null || end == null) {
            return;
        }
        Rectangle rect = buildRectangle(start, end);
        Graphics2D g2 = (Graphics2D) g.create();
        // 清除选中区域的背景色，使其完全透明
        g2.setColor(new Color(0, 0, 0, 0));
        g2.clearRect(rect.x, rect.y, rect.width, rect.height);
        // 绘制蓝色半透明边框
        g2.setColor(new Color(0, 150, 255, 180));
        g2.drawRect(rect.x, rect.y, rect.width, rect.height);
        g2.dispose();
    }

    /**
     * 根据起点和终点构建矩形
     * 自动计算左上角坐标和宽高（支持反向拖拽）
     */
    private Rectangle buildRectangle(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        int w = Math.abs(a.x - b.x);
        int h = Math.abs(a.y - b.y);
        return new Rectangle(x, y, w, h);
    }

    /**
     * 静态工厂方法：显示选择窗口并等待用户选择
     * @return 用户选择的屏幕区域
     */
    public static Rectangle selectRegion() {
        SelectionWindow window = new SelectionWindow();
        window.setVisible(true);
        window.applyExcludeFromCapture();
        return window.waitForSelection();
    }

    private void applyExcludeFromCapture() {
        if (!isWindows()) {
            return;
        }
        HWND hwnd = getHwnd();
        if (hwnd == null) {
            return;
        }
        try {
            User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
        } catch (Throwable ignored) {
            // Best-effort: older Windows or restricted APIs may fail.
        }
    }

    private HWND getHwnd() {
        Pointer pointer = Native.getComponentPointer(this);
        if (pointer == null) {
            return null;
        }
        return new HWND(pointer);
    }

    private static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    private interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetWindowDisplayAffinity(HWND hWnd, int dwAffinity);
    }
}
