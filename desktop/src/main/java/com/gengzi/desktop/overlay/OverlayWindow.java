package com.gengzi.desktop.overlay;

import com.gengzi.desktop.i18n.I18n;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Locale;
import com.sun.jna.win32.W32APIOptions;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

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
    private static final int OVERLAY_WIDTH = 420;
    private static final int OVERLAY_MIN_HEIGHT = 200;
    private static final int OVERLAY_MAX_HEIGHT = 1200;
    private static final Color OVERLAY_BG = new Color(40, 40, 40, 120);
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
    private static final int WDA_MONITOR = 0x00000001;
    private static final int WDA_NONE = 0x00000000;
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private final OverlayPanel panel;
    private String targetDisplayId;
    private boolean excludeFromCapture;
    private Rectangle lastRegion;

    /**
     * 初始化浮层窗口
     * 设置透明背景、置顶、不可聚焦等属性
     */
    public OverlayWindow() {
        this("", true);
    }

    public OverlayWindow(String targetDisplayId) {
        this(targetDisplayId, true);
    }

    public OverlayWindow(String targetDisplayId, boolean excludeFromCapture) {
        this.targetDisplayId = targetDisplayId == null ? "" : targetDisplayId.trim().toLowerCase(Locale.ROOT);
        this.excludeFromCapture = excludeFromCapture;
        panel = new OverlayPanel();
        // Non-transparent background so the overlay is readable.
        setBackground(OVERLAY_BG);
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
        lastRegion = region;
        Rectangle targetScreen = getTargetScreenBounds();
        if (region != null) {
            if (!targetScreen.intersects(region)) {
                setBounds(calculateOverlayBoundsForScreen(targetScreen));
            } else {
                setBounds(calculateOverlayBounds(region, targetScreen));
            }
        } else if (!targetDisplayId.isBlank()) {
            setBounds(calculateOverlayBoundsForScreen(targetScreen));
        }
        if (!isVisible()) {
            setVisible(true);
            enableClickThrough();
            applyDisplayAffinity();
        }
        repaint();
    }

    public void setTargetDisplayId(String targetDisplayId) {
        this.targetDisplayId = targetDisplayId == null ? "" : targetDisplayId.trim().toLowerCase(Locale.ROOT);
    }

    public void setExcludeFromCapture(boolean excludeFromCapture) {
        this.excludeFromCapture = excludeFromCapture;
        if (isDisplayable()) {
            applyDisplayAffinity();
        }
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
        if (isVisible()) {
            SwingUtilities.invokeLater(this::refreshBoundsForContent);
        }
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
        if (isWindows()) {
            int exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
            exStyle = exStyle | WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT | WinUser.WS_EX_COMPOSITED;
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, exStyle);
        }
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

    private void applyDisplayAffinity() {
        if (!isWindows()) {
            return;
        }
        HWND hwnd = getHwnd();
        if (hwnd == null) {
            return;
        }
        try {
            int affinity = excludeFromCapture ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE;
            boolean ok = User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, affinity);
            if (!ok && excludeFromCapture) {
                User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_MONITOR);
            }
        } catch (Throwable ignored) {
            // Best-effort: older Windows or restricted APIs may fail.
        }
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

    private static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    private Rectangle calculateOverlayBounds(Rectangle region, Rectangle screen) {
        int maxWidth = Math.max(120, Math.min(OVERLAY_WIDTH, screen.width - OVERLAY_GAP * 2));
        int preferredHeight = calculatePreferredHeight(maxWidth, screen);

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

    private Rectangle calculateOverlayBoundsForScreen(Rectangle screen) {
        int width = Math.max(120, Math.min(OVERLAY_WIDTH, screen.width - OVERLAY_GAP * 2));
        int height = calculatePreferredHeight(width, screen);
        int x = screen.x + screen.width - OVERLAY_GAP - width;
        int y = screen.y + OVERLAY_GAP;
        return new Rectangle(x, y, width, height);
    }

    private void refreshBoundsForContent() {
        Rectangle targetScreen = getTargetScreenBounds();
        Rectangle region = lastRegion;
        if (region != null) {
            if (!targetScreen.intersects(region)) {
                setBounds(calculateOverlayBoundsForScreen(targetScreen));
            } else {
                setBounds(calculateOverlayBounds(region, targetScreen));
            }
        } else if (!targetDisplayId.isBlank()) {
            setBounds(calculateOverlayBoundsForScreen(targetScreen));
        }
    }

    private int calculatePreferredHeight(int width, Rectangle screen) {
        int maxHeight = Math.min(OVERLAY_MAX_HEIGHT, screen.height - OVERLAY_GAP * 2);
        int contentHeight = panel.getPreferredHeightForWidth(width);
        return Math.max(OVERLAY_MIN_HEIGHT, Math.min(contentHeight, maxHeight));
    }

    private Rectangle getTargetScreenBounds() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (targetDisplayId.isBlank()) {
            return env.getMaximumWindowBounds();
        }
        for (java.awt.GraphicsDevice device : env.getScreenDevices()) {
            String id = device.getIDstring();
            if (id != null && id.toLowerCase(Locale.ROOT).contains(targetDisplayId)) {
                return device.getDefaultConfiguration().getBounds();
            }
        }
        return env.getMaximumWindowBounds();
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
        private static final Parser MARKDOWN_PARSER = Parser.builder().build();
        private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .build();
        private final JEditorPane editor;
        private final JScrollPane scrollPane;
        private String text = "";
        private final HTMLEditorKit htmlKit;

        private OverlayPanel() {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBackground(OVERLAY_BG);

            htmlKit = new HTMLEditorKit();
            htmlKit.getStyleSheet().addRule("body { margin:0; padding:0; color:#ffffff; "
                + "font-family:" + OVERLAY_FONT.getFamily() + "; font-size:16px; line-height:1.4; }");
            htmlKit.getStyleSheet().addRule("h1 { margin:14px 0 10px 0; }");
            htmlKit.getStyleSheet().addRule("h2 { margin:12px 0 8px 0; }");
            htmlKit.getStyleSheet().addRule("h3 { margin:10px 0 6px 0; }");
            htmlKit.getStyleSheet().addRule("p { margin:6px 0; }");
            htmlKit.getStyleSheet().addRule("pre { margin:8px 0; padding:8px; background:#111; "
                + "border:1px solid #2a2a2a; white-space:pre-wrap; }");
            htmlKit.getStyleSheet().addRule("code { font-family:" + OVERLAY_FONT.getFamily() + "; }");
            editor = new JEditorPane();
            editor.setEditorKit(htmlKit);
            editor.setContentType("text/html");
            editor.setDocument(htmlKit.createDefaultDocument());
            editor.setEditable(false);
            editor.setOpaque(false);
            editor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            editor.setFont(OVERLAY_FONT);
            editor.setForeground(Color.WHITE);
            editor.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));

            scrollPane = new JScrollPane(editor);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            add(scrollPane, BorderLayout.CENTER);
        }

        /**
         * ???????????????????????????????????????
         * @param text ???????????????null?????????????????????No answer yet"
         */
        public void setText(String text) {
            this.text = text == null ? "" : text;
            String content = this.text.isBlank() ? I18n.tr("overlay.no_answer") : this.text;
            editor.setText(renderMarkdown(content));
            editor.revalidate();
            editor.setCaretPosition(editor.getDocument().getLength());
            repaint();
        }

        private int getPreferredHeightForWidth(int width) {
            int contentWidth = Math.max(80, width - PADDING * 2);
            View root = editor.getUI().getRootView(editor);
            root.setSize(contentWidth, Integer.MAX_VALUE);
            int preferred = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            return Math.max(OVERLAY_MIN_HEIGHT, preferred + PADDING * 2);
        }

        /**
         * ???????????????????????????????????????????????? + Markdown??????
         * @param g ???????????????
         */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            // ?????????????????????
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }

        private static String renderMarkdown(String input) {
            String normalized = input == null ? "" : input.replace("\r\n", "\n").replace('\r', '\n');
            Node document = MARKDOWN_PARSER.parse(normalized);
            String htmlBody = MARKDOWN_RENDERER.render(document);
            StringBuilder html = new StringBuilder(htmlBody.length() + 32);
            html.append("<html><body>")
                .append(htmlBody)
                .append("</body></html>");
            return html.toString();
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

    private interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetWindowDisplayAffinity(HWND hWnd, int dwAffinity);
    }
}
