package com.gengzi.desktop;

import com.gengzi.desktop.config.AppConfig;
import com.gengzi.desktop.llm.BackendClient;
import com.gengzi.desktop.ocr.OcrService;
import com.gengzi.desktop.overlay.OverlayWindow;
import com.gengzi.desktop.ui.ControlFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

public class DesktopApp {
    public static void main(String[] args) {
        printDisplays();
        AppConfig config = new AppConfig();

        String backendUrl = config.get("BACKEND_URL", "http://localhost:8080");
        String overlayDisplayId = config.get("OVERLAY_DISPLAY_ID", "");
        boolean overlayExcludeFromCapture = Boolean.parseBoolean(
            config.get("OVERLAY_EXCLUDE_FROM_CAPTURE", "true")
        );
        String periscopeDisplayId = config.get("PERISCOPE_DISPLAY_ID", "");
        int periscopeRefreshMs = config.getInt("PERISCOPE_REFRESH_MS", 120);
        int periscopeWidth = config.getInt("PERISCOPE_WINDOW_WIDTH", 640);
        int periscopeHeight = config.getInt("PERISCOPE_WINDOW_HEIGHT", 360);

        BackendClient backendClient = new BackendClient(backendUrl);
        OcrService ocrService = new OcrService(backendClient);
        OverlayWindow overlay = new OverlayWindow(overlayDisplayId, overlayExcludeFromCapture);

        SwingUtilities.invokeLater(() -> {
            ControlFrame frame = new ControlFrame(
                overlay,
                ocrService,
                overlayDisplayId,
                overlayExcludeFromCapture,
                periscopeDisplayId,
                periscopeRefreshMs,
                periscopeWidth,
                periscopeHeight
            );
            frame.setVisible(true);
        });
    }

    private static void printDisplays() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = env.getScreenDevices();
        System.out.println("Detected displays: " + devices.length);
        for (int i = 0; i < devices.length; i++) {
            GraphicsDevice device = devices[i];
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            System.out.println("[" + i + "] " + device.getIDstring()
                + " bounds=" + bounds.x + "," + bounds.y + " " + bounds.width + "x" + bounds.height);
        }
    }
}
