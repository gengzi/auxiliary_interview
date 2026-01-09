package com.gengzi.desktop;

import com.gengzi.desktop.config.AppConfig;
import com.gengzi.desktop.llm.BackendClient;
import com.gengzi.desktop.ocr.OcrService;
import com.gengzi.desktop.overlay.OverlayWindow;
import com.gengzi.desktop.ui.ControlFrame;

import javax.swing.SwingUtilities;

public class DesktopApp {
    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        String backendUrl = config.get("BACKEND_URL", "http://localhost:8080");

        BackendClient backendClient = new BackendClient(backendUrl);
        OcrService ocrService = new OcrService(backendClient);
        OverlayWindow overlay = new OverlayWindow();

        SwingUtilities.invokeLater(() -> {
            ControlFrame frame = new ControlFrame(overlay, ocrService);
            frame.setVisible(true);
        });
    }
}
