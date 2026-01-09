package com.gengzi.desktop.ui;

import com.gengzi.desktop.hotkey.SystemHotkey;
import com.gengzi.desktop.i18n.I18n;
import com.gengzi.desktop.ocr.OcrService;
import com.gengzi.desktop.overlay.OverlayWindow;
import com.gengzi.desktop.overlay.SelectionWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class ControlFrame extends JFrame {
    private final OverlayWindow overlay;
    private final OcrService ocrService;

    private Rectangle region;
    private final JLabel statusLabel;
    private final JLabel languageLabel;
    private final JLabel overlayDisplayLabel;
    private final JLabel periscopeDisplayLabel;
    private final JLabel displayListLabel;
    private final JButton selectButton;
    private final JButton solveButton;
    private final JButton toggleOverlayButton;
    private final JButton applyDisplayButton;
    private final JButton refreshDisplaysButton;
    private final JButton periscopeToggleButton;
    private final JComboBox<LocaleOption> languageBox;
    private final JTextField overlayDisplayField;
    private final JCheckBox overlayExcludeCheck;
    private final JTextField periscopeDisplayField;
    private final JTextArea displayListArea;
    private final int periscopeRefreshMs;
    private final int periscopeWidth;
    private final int periscopeHeight;
    private PeriscopeWindow periscopeWindow;
    private String statusKey;
    private Object[] statusArgs;

    // 添加处理状态标志，防止重复执行
    private volatile boolean isProcessing = false;

    // 全局快捷键管理器
    private SystemHotkey systemHotkey = null;

    // 当前快捷键注册的 ID
    private int currentHotKeyId = -1;

    public ControlFrame(
        OverlayWindow overlay,
        OcrService ocrService,
        String overlayDisplayId,
        boolean overlayExcludeFromCapture,
        String periscopeDisplayId,
        int periscopeRefreshMs,
        int periscopeWidth,
        int periscopeHeight
    ) {
        this.overlay = overlay;
        this.ocrService = ocrService;
        this.periscopeRefreshMs = periscopeRefreshMs;
        this.periscopeWidth = periscopeWidth;
        this.periscopeHeight = periscopeHeight;

        setTitle(I18n.tr("app.title"));
        setType(Type.UTILITY);
        setSize(640, 380);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        selectButton = new JButton();
        solveButton = new JButton();
        toggleOverlayButton = new JButton();
        applyDisplayButton = new JButton();
        refreshDisplaysButton = new JButton();
        periscopeToggleButton = new JButton();

        statusLabel = new JLabel();
        languageLabel = new JLabel();
        overlayDisplayLabel = new JLabel();
        periscopeDisplayLabel = new JLabel();
        displayListLabel = new JLabel();
        languageBox = new JComboBox<>(new DefaultComboBoxModel<>(new LocaleOption[] {
            new LocaleOption(Locale.SIMPLIFIED_CHINESE, "language.zh"),
            new LocaleOption(Locale.ENGLISH, "language.en")
        }));
        overlayDisplayField = new JTextField(14);
        overlayDisplayField.setText(overlayDisplayId == null ? "" : overlayDisplayId);
        overlayExcludeCheck = new JCheckBox();
        overlayExcludeCheck.setSelected(overlayExcludeFromCapture);
        periscopeDisplayField = new JTextField(14);
        periscopeDisplayField.setText(periscopeDisplayId == null ? "" : periscopeDisplayId);
        displayListArea = new JTextArea(4, 36);
        displayListArea.setEditable(false);
        displayListArea.setLineWrap(true);
        displayListArea.setWrapStyleWord(true);
        displayListArea.setText(buildDisplayList());

        selectButton.addActionListener(e -> selectRegion());
        solveButton.addActionListener(e -> captureAndSolve());
        toggleOverlayButton.addActionListener(e -> toggleOverlay());
        applyDisplayButton.addActionListener(e -> applyDisplaySettings());
        refreshDisplaysButton.addActionListener(e -> refreshDisplayList());
        periscopeToggleButton.addActionListener(e -> togglePeriscope());
        languageBox.addActionListener(e -> onLocaleSelected());

        JPanel buttons = new JPanel();
        buttons.add(selectButton);
        buttons.add(solveButton);
        buttons.add(toggleOverlayButton);

        JPanel settings = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        settings.add(overlayDisplayLabel, gbc);
        gbc.gridx = 1;
        settings.add(overlayDisplayField, gbc);
        gbc.gridx = 2;
        settings.add(applyDisplayButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        settings.add(new JLabel(""), gbc);
        gbc.gridx = 1;
        settings.add(overlayExcludeCheck, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        settings.add(periscopeDisplayLabel, gbc);
        gbc.gridx = 1;
        settings.add(periscopeDisplayField, gbc);
        gbc.gridx = 2;
        settings.add(periscopeToggleButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        settings.add(displayListLabel, gbc);
        gbc.gridx = 2;
        settings.add(refreshDisplaysButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        settings.add(new JScrollPane(displayListArea), gbc);

        JPanel languagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        languagePanel.add(languageLabel);
        languagePanel.add(languageBox);

        setLayout(new BorderLayout(8, 8));
        add(languagePanel, BorderLayout.NORTH);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buttons);
        center.add(settings);
        add(center, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        statusKey = "status.region_not_set";
        statusArgs = new Object[0];
        selectLocale(I18n.getLocale());
        updateTexts();
        applyDisplaySettings();
        refreshDisplayList();
        if (periscopeDisplayId != null && !periscopeDisplayId.isBlank()) {
            startPeriscope();
        }

        // 注册系统级全局快捷键 Ctrl+P
        registerSystemGlobalHotkey();
    }

    /**
     * 注册系统级全局快捷键 Ctrl+P
     * 使用 Windows API，即使窗口最小化或失去焦点也能触发
     */
    private void registerSystemGlobalHotkey() {
        System.out.println("初始化系统级全局快捷键...");

        systemHotkey = new SystemHotkey(() -> {
            System.out.println("全局快捷键 Ctrl+P 被触发！");
            System.out.println("当前 isProcessing 状态: " + isProcessing);

            // 检查是否正在处理
            if (isProcessing) {
                System.out.println("正在处理中，忽略本次触发");
                return;
            }

            // 在 EDT 线程中执行截图操作
            SwingUtilities.invokeLater(() -> {
                captureAndSolve();
            });
        });

        // 注册快捷键
        boolean success = systemHotkey.register(this);

        if (!success) {
            System.err.println("警告：全局快捷键注册失败，请使用按钮触发");
        }
    }

    private void updateTexts() {
        setTitle(I18n.tr("app.title"));
        selectButton.setText(I18n.tr("button.select_region"));
        solveButton.setText(I18n.tr("button.capture_solve"));
        toggleOverlayButton.setText(I18n.tr("button.toggle_overlay"));
        applyDisplayButton.setText(I18n.tr("button.apply_display"));
        refreshDisplaysButton.setText(I18n.tr("button.refresh_displays"));
        languageLabel.setText(I18n.tr("label.language"));
        overlayDisplayLabel.setText(I18n.tr("label.overlay_display_id"));
        periscopeDisplayLabel.setText(I18n.tr("label.periscope_display_id"));
        displayListLabel.setText(I18n.tr("label.display_list"));
        overlayExcludeCheck.setText(I18n.tr("label.exclude_capture"));
        updatePeriscopeButtonLabel();
        applyStatus();
        languageBox.repaint();
    }

    private void updatePeriscopeButtonLabel() {
        if (periscopeWindow == null) {
            periscopeToggleButton.setText(I18n.tr("button.start_periscope"));
        } else {
            periscopeToggleButton.setText(I18n.tr("button.stop_periscope"));
        }
    }

    private void applyDisplaySettings() {
        String overlayDisplayId = overlayDisplayField.getText();
        overlay.setTargetDisplayId(overlayDisplayId);
        overlay.setExcludeFromCapture(overlayExcludeCheck.isSelected());
        if (overlay.isVisible()) {
            if (region != null) {
                overlay.showOverlay(region);
            } else {
                overlay.showOverlay(null);
            }
        }
    }

    private void togglePeriscope() {
        if (periscopeWindow == null) {
            startPeriscope();
        } else {
            stopPeriscope();
        }
    }

    private void startPeriscope() {
        String displayId = periscopeDisplayField.getText();
        if (displayId == null || displayId.isBlank()) {
            setStatus("status.periscope_missing_id");
            return;
        }
        try {
            periscopeWindow = new PeriscopeWindow(displayId, periscopeRefreshMs, periscopeWidth, periscopeHeight);
            periscopeWindow.start();
            setStatus("status.periscope_started");
        } catch (Exception ex) {
            periscopeWindow = null;
            setStatus("status.periscope_failed", ex.getMessage());
        } finally {
            updatePeriscopeButtonLabel();
        }
    }

    private void stopPeriscope() {
        if (periscopeWindow != null) {
            periscopeWindow.stop();
            periscopeWindow = null;
            setStatus("status.periscope_stopped");
        }
        updatePeriscopeButtonLabel();
    }

    private void refreshDisplayList() {
        displayListArea.setText(buildDisplayList());
    }

    private String buildDisplayList() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = env.getScreenDevices();
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.tr("label.detected_displays", devices.length)).append('\n');
        for (int i = 0; i < devices.length; i++) {
            GraphicsDevice device = devices[i];
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            sb.append('[').append(i).append("] ")
                .append(device.getIDstring())
                .append(" bounds=")
                .append(bounds.x).append(',').append(bounds.y).append(' ')
                .append(bounds.width).append('x').append(bounds.height)
                .append('\n');
        }
        return sb.toString().trim();
    }

    private void onLocaleSelected() {
        LocaleOption selected = (LocaleOption) languageBox.getSelectedItem();
        if (selected == null) {
            return;
        }
        I18n.setLocale(selected.locale);
        updateTexts();
        refreshDisplayList();
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
        // 检查是否正在处理，防止重复执行
        if (isProcessing) {
            System.out.println("正在处理中，忽略本次请求");
            setStatus("status.analyzing");
            return;
        }

        if (region == null) {
            System.out.println("区域未设置");
            setStatus("status.region_not_set");
            return;
        }

        System.out.println("开始截图并解答...");

        // 设置处理标志
        isProcessing = true;
        setStatus("status.analyzing");

        // 禁用按钮，防止重复点击
        SwingUtilities.invokeLater(() -> {
            solveButton.setEnabled(false);
            selectButton.setEnabled(false);
        });

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

                System.out.println("处理完成");

                SwingUtilities.invokeLater(() -> {
                    setStatus("status.done");
                    // 恢复按钮状态
                    solveButton.setEnabled(true);
                    selectButton.setEnabled(true);
                    // 重置处理标志，必须在最后执行
                    isProcessing = false;
                    System.out.println("状态已重置，可以再次执行");
                });

            } catch (Exception ex) {
                System.err.println("处理出错: " + ex.getMessage());
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    overlay.setAnswer(I18n.tr("overlay.error", ex.getMessage()));
                    setStatus("status.error");
                    // 恢复按钮状态
                    solveButton.setEnabled(true);
                    selectButton.setEnabled(true);
                    // 重置处理标志
                    isProcessing = false;
                    System.out.println("出错后状态已重置");
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
