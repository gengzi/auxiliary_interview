package com.gengzi.desktop.ui;

import com.gengzi.desktop.i18n.I18n;
import com.gengzi.desktop.ocr.OcrService;
import com.gengzi.desktop.overlay.OverlayWindow;
import com.gengzi.desktop.overlay.SelectionWindow;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class ControlFrame extends JFrame {
    private final OverlayWindow overlay;
    private final OcrService ocrService;

    private Rectangle region;
    private final JLabel statusLabel;
    private final JLabel languageLabel;
    private final JButton selectButton;
    private final JButton solveButton;
    private final JButton toggleOverlayButton;
    private final JComboBox<LocaleOption> languageBox;
    private String statusKey;
    private Object[] statusArgs;

    public ControlFrame(OverlayWindow overlay, OcrService ocrService) {
        this.overlay = overlay;
        this.ocrService = ocrService;

        setTitle(I18n.tr("app.title"));
        setType(Type.UTILITY);
        setSize(420, 190);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        selectButton = new JButton();
        solveButton = new JButton();
        toggleOverlayButton = new JButton();

        statusLabel = new JLabel();
        languageLabel = new JLabel();
        languageBox = new JComboBox<>(new DefaultComboBoxModel<>(new LocaleOption[] {
            new LocaleOption(Locale.SIMPLIFIED_CHINESE, "language.zh"),
            new LocaleOption(Locale.ENGLISH, "language.en")
        }));

        selectButton.addActionListener(e -> selectRegion());
        solveButton.addActionListener(e -> captureAndSolve());
        toggleOverlayButton.addActionListener(e -> toggleOverlay());
        languageBox.addActionListener(e -> onLocaleSelected());

        JPanel buttons = new JPanel();
        buttons.add(selectButton);
        buttons.add(solveButton);
        buttons.add(toggleOverlayButton);

        JPanel languagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        languagePanel.add(languageLabel);
        languagePanel.add(languageBox);

        setLayout(new BorderLayout(8, 8));
        add(languagePanel, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        statusKey = "status.region_not_set";
        statusArgs = new Object[0];
        selectLocale(I18n.getLocale());
        updateTexts();
    }

    private void updateTexts() {
        setTitle(I18n.tr("app.title"));
        selectButton.setText(I18n.tr("button.select_region"));
        solveButton.setText(I18n.tr("button.capture_solve"));
        toggleOverlayButton.setText(I18n.tr("button.toggle_overlay"));
        languageLabel.setText(I18n.tr("label.language"));
        applyStatus();
        languageBox.repaint();
    }

    private void onLocaleSelected() {
        LocaleOption selected = (LocaleOption) languageBox.getSelectedItem();
        if (selected == null) {
            return;
        }
        I18n.setLocale(selected.locale);
        updateTexts();
        overlay.repaint();
    }

    private void selectLocale(Locale locale) {
        for (int i = 0; i < languageBox.getItemCount(); i++) {
            LocaleOption option = languageBox.getItemAt(i);
            if (option != null && option.locale.equals(locale)) {
                languageBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void setStatus(String key, Object... args) {
        statusKey = key;
        statusArgs = args == null ? new Object[0] : args.clone();
        applyStatus();
    }

    private void applyStatus() {
        if (statusKey == null) {
            statusLabel.setText("");
            return;
        }
        statusLabel.setText(I18n.tr(statusKey, statusArgs));
    }

    private void selectRegion() {
        setStatus("status.selecting");
        new Thread(() -> {
            Rectangle selected = SelectionWindow.selectRegion();
            if (selected != null && selected.width > 0 && selected.height > 0) {
                region = selected;
                overlay.showOverlay(region);
                overlay.setAnswer(I18n.tr("overlay.region_selected"));
                SwingUtilities.invokeLater(() -> setStatus("status.region", region));
            } else {
                SwingUtilities.invokeLater(() -> setStatus("status.region_not_set"));
            }
        }, "region-select").start();
    }

    private void captureAndSolve() {
        if (region == null) {
            setStatus("status.region_not_set");
            return;
        }
        setStatus("status.analyzing");
        new Thread(() -> {
            try {
                Robot robot = new Robot();
                BufferedImage image = robot.createScreenCapture(region);

                // 使用StringBuilder累积流式返回的文本
                StringBuilder fullAnswer = new StringBuilder();
                overlay.setAnswer(I18n.tr("overlay.processing"));

                // 使用流式API
                ocrService.recognizeStream(image, chunk -> {
                    // 累积每个文本块
                    fullAnswer.append(chunk);
                    // 在EDT线程中更新UI显示
                    SwingUtilities.invokeLater(() -> {
                        overlay.setAnswer(fullAnswer.toString());
                    });
                });

                SwingUtilities.invokeLater(() -> setStatus("status.done"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    overlay.setAnswer(I18n.tr("overlay.error", ex.getMessage()));
                    setStatus("status.error");
                });
            }
        }, "solve").start();
    }

    private void toggleOverlay() {
        if (overlay.isVisible()) {
            overlay.hideOverlay();
        } else if (region != null) {
            overlay.showOverlay(region);
        }
    }

    private static final class LocaleOption {
        private final Locale locale;
        private final String labelKey;

        private LocaleOption(Locale locale, String labelKey) {
            this.locale = locale;
            this.labelKey = labelKey;
        }

        @Override
        public String toString() {
            return I18n.tr(labelKey);
        }
    }
}
