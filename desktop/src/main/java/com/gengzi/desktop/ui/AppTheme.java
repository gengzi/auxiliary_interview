package com.gengzi.desktop.ui;

import com.formdev.flatlaf.FlatIntelliJLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppTheme {
    private static final Color CHROME_RED = new Color(219, 68, 55);
    private static final Color CHROME_YELLOW = new Color(244, 180, 0);
    private static final Color CHROME_GREEN = new Color(15, 157, 88);
    private static final Color CHROME_BLUE = new Color(66, 133, 244);
    private static final Color CHROME_WHITE = new Color(255, 255, 255);
    private static final List<Image> APP_ICONS = createAppIcons();

    private AppTheme() {
    }

    public static void install() {
        try {
            FlatIntelliJLaf.setup();
        } catch (Exception ignored) {
            // Fall back to the default look and feel.
        }
        configureDefaults();
        applyTaskbarIcon();
    }

    public static void applyWindowIcon(Window window) {
        if (window == null || APP_ICONS.isEmpty()) {
            return;
        }
        window.setIconImages(APP_ICONS);
    }

    private static void configureDefaults() {
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.borderWidth", 1);
        UIManager.put("Button.margin", new Insets(6, 12, 6, 12));
        UIManager.put("TextComponent.margin", new Insets(6, 8, 6, 8));

        Font uiFont = resolveUiFont(13);
        if (uiFont != null) {
            UIManager.put("defaultFont", uiFont);
        }
    }

    private static void applyTaskbarIcon() {
        if (!Taskbar.isTaskbarSupported() || APP_ICONS.isEmpty()) {
            return;
        }
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(APP_ICONS.get(APP_ICONS.size() - 1));
            }
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    private static List<Image> createAppIcons() {
        int[] sizes = new int[] { 16, 20, 24, 32, 48, 64, 128, 256 };
        List<Image> icons = new ArrayList<>(sizes.length);
        for (int size : sizes) {
            icons.add(drawChromeIcon(size));
        }
        return Collections.unmodifiableList(icons);
    }

    private static Image drawChromeIcon(int size) {
        int pad = Math.max(1, Math.round(size * 0.08f));
        int diameter = size - pad * 2;
        int x = pad;
        int y = pad;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2.setColor(CHROME_RED);
        g2.fill(new Arc2D.Double(x, y, diameter, diameter, 90, -120, Arc2D.PIE));
        g2.setColor(CHROME_YELLOW);
        g2.fill(new Arc2D.Double(x, y, diameter, diameter, -30, -120, Arc2D.PIE));
        g2.setColor(CHROME_GREEN);
        g2.fill(new Arc2D.Double(x, y, diameter, diameter, -150, -120, Arc2D.PIE));

        int ring = Math.max(2, Math.round(diameter * 0.56f));
        int ringX = x + (diameter - ring) / 2;
        int ringY = y + (diameter - ring) / 2;
        g2.setColor(CHROME_WHITE);
        g2.fillOval(ringX, ringY, ring, ring);

        int core = Math.max(2, Math.round(diameter * 0.36f));
        int coreX = x + (diameter - core) / 2;
        int coreY = y + (diameter - core) / 2;
        g2.setColor(CHROME_BLUE);
        g2.fillOval(coreX, coreY, core, core);

        g2.dispose();
        return image;
    }

    private static Font resolveUiFont(int size) {
        // 优先使用支持中文的字体
        String[] candidates = new String[] {
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "SimSun",
            "SimHei",
            "Segoe UI",
            "Noto Sans CJK SC",
            "SansSerif"
        };
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames();
        for (String candidate : candidates) {
            for (String name : available) {
                if (name.equalsIgnoreCase(candidate)) {
                    System.out.println("使用字体: " + name);
                    return new Font(name, Font.PLAIN, size);
                }
            }
        }
        System.out.println("未找到候选字体，使用默认字体");
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }
}
