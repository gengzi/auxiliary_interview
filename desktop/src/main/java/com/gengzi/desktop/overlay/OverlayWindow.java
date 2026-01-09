package com.gengzi.desktop.overlay;

import com.gengzi.desktop.i18n.I18n;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.JComponent;
import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * 透明浮层窗口，用于在屏幕指定区域显示LLM回答结果
 * 核心特性：
 * 1. 始终置顶显示（AlwaysOnTop）
 * 2. 鼠标点击穿透（Click-Through）- 不阻挡用户操作
 * 3. 半透明背景 + 白色文字显示
 * 4. 自动文本换行
 */
public class OverlayWindow extends JWindow {
    private static final int OVERLAY_GAP = 8;
    private static final int OVERLAY_WIDTH = 320;
    private static final int OVERLAY_MIN_HEIGHT = 160;
    private static final int OVERLAY_MAX_HEIGHT = 360;
    private final OverlayPanel panel;

    /**
     * 初始化浮层窗口
     * 设置透明背景、置顶、不可聚焦等属性
     */
    public OverlayWindow() {
        panel = new OverlayPanel();
        // 完全透明背景
        setBackground(new Color(0, 0, 0, 0));
        // 窗口始终显示在最上层
        setAlwaysOnTop(true);
        // 窗口不可获取焦点，避免干扰其他窗口操作
        setFocusableWindowState(false);
        setContentPane(panel);
    }

    /**
     * 在指定区域显示浮层
     * @param region 屏幕区域坐标，如果为null则使用当前bounds
     */
    public void showOverlay(Rectangle region) {
        if (region != null) {
            setBounds(calculateOverlayBounds(region));
        }
        if (!isVisible()) {
            setVisible(true);
            enableClickThrough();
        }
        repaint();
    }

    /**
     * 隐藏浮层窗口
     */
    public void hideOverlay() {
        setVisible(false);
    }

    /**
     * 设置显示的文本内容
     * @param text LLM返回的答案文本
     */
    public void setAnswer(String text) {
        panel.setText(text);
    }

    /**
     * 启用鼠标点击穿透功能
     * 使用Windows API设置窗口扩展样式，使鼠标事件穿透窗口传递到下层
     * WS_EX_LAYERED: 分层窗口，支持透明度
     * WS_EX_TRANSPARENT: 透明窗口，鼠标点击穿透
     * WS_EX_TOOLWINDOW: 工具窗口，不在任务栏显示
     */
    private void enableClickThrough() {
        HWND hwnd = getHwnd();
        if (hwnd == null) {
            return;
        }
        int exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
        exStyle = exStyle | WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT | WinUser.WS_EX_COMPOSITED;
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, exStyle);
    }

    /**
     * 获取窗口的HWND句柄
     * @return Windows窗口句柄，如果获取失败返回null
     */
    private HWND getHwnd() {
        Pointer pointer = Native.getComponentPointer(this);
        if (pointer == null) {
            return null;
        }
        return new HWND(pointer);
    }

    private Rectangle calculateOverlayBounds(Rectangle region) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxWidth = Math.max(120, Math.min(OVERLAY_WIDTH, screen.width - OVERLAY_GAP * 2));
        int preferredHeight = Math.min(
            Math.max(OVERLAY_MIN_HEIGHT, region.height),
            Math.min(OVERLAY_MAX_HEIGHT, screen.height - OVERLAY_GAP * 2)
        );

        int spaceRight = (screen.x + screen.width) - (region.x + region.width) - OVERLAY_GAP;
        int spaceLeft = region.x - screen.x - OVERLAY_GAP;

        int width = maxWidth;
        int x;
        if (spaceRight >= maxWidth) {
            x = region.x + region.width + OVERLAY_GAP;
        } else if (spaceLeft >= maxWidth) {
            x = region.x - OVERLAY_GAP - maxWidth;
        } else if (spaceRight >= spaceLeft && spaceRight > 0) {
            width = Math.max(120, Math.min(maxWidth, spaceRight));
            x = region.x + region.width + OVERLAY_GAP;
        } else if (spaceLeft > 0) {
            width = Math.max(120, Math.min(maxWidth, spaceLeft));
            x = region.x - OVERLAY_GAP - width;
        } else {
            int spaceAbove = region.y - screen.y - OVERLAY_GAP;
            int spaceBelow = (screen.y + screen.height) - (region.y + region.height) - OVERLAY_GAP;
            int height = Math.min(
                preferredHeight,
                Math.max(OVERLAY_MIN_HEIGHT, Math.max(spaceAbove, spaceBelow))
            );
            int y = spaceBelow >= height
                ? region.y + region.height + OVERLAY_GAP
                : region.y - OVERLAY_GAP - height;
            int fallbackWidth = Math.min(maxWidth, screen.width - OVERLAY_GAP * 2);
            int fallbackX = clamp(
                region.x,
                screen.x + OVERLAY_GAP,
                screen.x + screen.width - OVERLAY_GAP - fallbackWidth
            );
            return new Rectangle(fallbackX, y, fallbackWidth, height);
        }

        int height = Math.min(preferredHeight, screen.height - OVERLAY_GAP * 2);
        int y = clamp(
            region.y,
            screen.y + OVERLAY_GAP,
            screen.y + screen.height - OVERLAY_GAP - height
        );
        return new Rectangle(x, y, width, height);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 浮层内容面板，负责绘制半透明背景和文本
     */
    private static class OverlayPanel extends JComponent {
        private static final int PADDING = 12;
        private static final Font OVERLAY_FONT = resolveFont();
        private String text = "";

        /**
         * 设置要显示的文本并触发重绘
         * @param text 文本内容，null或空字符串显示"No answer yet"
         */
        public void setText(String text) {
            this.text = text == null ? "" : text;
            repaint();
        }

        /**
         * 绘制浮层内容：半透明背景圆角矩形 + 白色文本
         * @param g 图形上下文
         */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            // 启用文本抗锯齿
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // 绘制半透明黑色背景，圆角矩形
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(0, 0, width, height, 12, 12);

            // 绘制白色文本
            g2.setColor(Color.WHITE);
            g2.setFont(OVERLAY_FONT);
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();

            // 计算最大可用宽度（减去左右padding）
            int maxWidth = Math.max(1, width - PADDING * 2);
            List<String> lines = wrapText(text, fm, maxWidth);

            // 逐行绘制文本
            int y = PADDING + fm.getAscent();
            for (String line : lines) {
                if (y > height - PADDING) {
                    break;
                }
                g2.drawString(line, PADDING, y);
                y += lineHeight;
            }
            g2.dispose();
        }

        /**
         * 文本自动换行处理
         * 按空格分词，根据maxWidth计算每行能容纳的单词数
         * @param input 原始文本
         * @param fm 字体度量信息
         * @param maxWidth 最大行宽（像素）
         * @return 换行后的文本行列表
         */
        private List<String> wrapText(String input, FontMetrics fm, int maxWidth) {
            List<String> lines = new ArrayList<>();
            if (input == null || input.isBlank()) {
                lines.add(I18n.tr("overlay.no_answer"));
                return lines;
            }
            // 移除回车符，按空白字符分词
            String[] words = input.replace("\r", "").split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() == 0) {
                    line.append(word);
                    continue;
                }
                String test = line + " " + word;
                if (fm.stringWidth(test) <= maxWidth) {
                    line.append(" ").append(word);
                } else {
                    // 当前行已满，添加到结果并开始新行
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            // 添加最后一行
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static Font resolveFont() {
            // Prefer fonts that render CJK well, then fall back to a common UI font.
            String[] candidates = new String[] {
                "Microsoft YaHei UI",
                "Microsoft YaHei",
                "SimHei",
                "PingFang SC",
                "Heiti SC",
                "WenQuanYi Micro Hei",
                "Noto Sans CJK SC",
                "Segoe UI"
            };
            String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
            for (String name : candidates) {
                for (String fontName : available) {
                    if (fontName.equalsIgnoreCase(name)) {
                        return new Font(fontName, Font.PLAIN, 16);
                    }
                }
            }
            return new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        }
    }
}
