package com.gengzi.desktop.ui;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.awt.AWTException;
import java.awt.Robot;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.W32APIOptions;

public class PeriscopeWindow extends JFrame {
    private static final int DEFAULT_REFRESH_MS = 120;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
    private static final int WDA_MONITOR = 0x00000001;
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private final ImagePanel imagePanel;
    private final Rectangle captureBounds;
    private final ScheduledExecutorService executor;
    private final Robot robot;
    private volatile boolean running;
    private final int refreshMs;

    public PeriscopeWindow(String sourceDisplayId, int refreshMs, int width, int height) throws AWTException {
        this.refreshMs = refreshMs > 0 ? refreshMs : DEFAULT_REFRESH_MS;
        GraphicsDevice device = resolveDisplay(sourceDisplayId);
        captureBounds = device.getDefaultConfiguration().getBounds();
        robot = new Robot();
        imagePanel = new ImagePanel();

        setTitle("Periscope");
        setType(Type.UTILITY);
        setSize(Math.max(240, width), Math.max(180, height));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setContentPane(imagePanel);
        setLocationOnPrimaryScreen();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                applyExcludeFromCapture();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                applyExcludeFromCapture();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                stop();
            }
        });

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "periscope-capture");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        setVisible(true);
        applyExcludeFromCapture();
        executor.scheduleAtFixedRate(this::captureAndRefresh, 0, refreshMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.shutdownNow();
        SwingUtilities.invokeLater(this::dispose);
    }

    private void captureAndRefresh() {
        if (!running) {
            return;
        }
        BufferedImage shot = robot.createScreenCapture(captureBounds);
        SwingUtilities.invokeLater(() -> {
            if (running) {
                imagePanel.setImage(shot);
            }
        });
    }

    private void setLocationOnPrimaryScreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        int x = bounds.x + Math.max(0, (bounds.width - getWidth()) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - getHeight()) / 2);
        setLocation(x, y);
    }

    private void applyExcludeFromCapture() {
        if (!isWindows() || !isDisplayable()) {
            return;
        }
        HWND hwnd = getHwnd();
        if (hwnd == null) {
            return;
        }
        try {
            boolean ok = User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
            if (!ok) {
                User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_MONITOR);
            }
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

    private static GraphicsDevice resolveDisplay(String displayId) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (displayId == null || displayId.isBlank()) {
            return env.getDefaultScreenDevice();
        }
        String needle = displayId.trim().toLowerCase(Locale.ROOT);
        for (GraphicsDevice device : env.getScreenDevices()) {
            String id = device.getIDstring();
            if (id != null && id.toLowerCase(Locale.ROOT).contains(needle)) {
                return device;
            }
        }
        return env.getDefaultScreenDevice();
    }

    private static final class ImagePanel extends JPanel {
        private volatile BufferedImage image;

        private ImagePanel() {
            setBackground(Color.BLACK);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(640, 360);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage current = image;
            if (current == null) {
                return;
            }
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double scale = Math.min(
                (double) panelWidth / current.getWidth(),
                (double) panelHeight / current.getHeight()
            );
            int drawWidth = Math.max(1, (int) Math.round(current.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(current.getHeight() * scale));
            int x = (panelWidth - drawWidth) / 2;
            int y = (panelHeight - drawHeight) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(current, x, y, drawWidth, drawHeight, null);
            g2.dispose();
        }
    }

    private interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetWindowDisplayAffinity(HWND hWnd, int dwAffinity);
    }
}
