package com.gengzi.desktop.ocr;

import com.gengzi.desktop.llm.BackendClient;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * OCR服务（实际使用LLM进行图片分析）
 * 支持流式和非流式两种模式
 */
public class OcrService {
    private static final int MAX_DIMENSION = 1280;
    private static final float JPEG_QUALITY = 0.7f;
    private final BackendClient backendClient;

    public OcrService(String tesseractPath, String tessdataPath, String language, BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    public OcrService(BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    /**
     * 分析图片并返回完整答案（非流式）
     * @param image 截图
     * @return LLM分析结果
     */
    public String recognize(BufferedImage image) throws Exception {
        byte[] imageData = encodeImage(image);
        return backendClient.solveWithImage(imageData, "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.");
    }

    /**
     * 分析图片并流式返回答案（流式）
     * @param image 截图
     * @param chunkConsumer 接收每个文本块的回调函数
     */
    public void recognizeStream(BufferedImage image, Consumer<String> chunkConsumer) throws Exception {
        byte[] imageData = encodeImage(image);
        backendClient.solveWithImageStream(imageData, "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.", chunkConsumer);
    }

    private byte[] encodeImage(BufferedImage image) throws IOException {
        BufferedImage scaled = scaleDown(normalizeImage(image));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(scaled, "png", baos);
            return baos.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(scaled, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private BufferedImage scaleDown(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxDim = Math.max(width, height);
        if (maxDim <= MAX_DIMENSION) {
            return image;
        }
        double scale = MAX_DIMENSION / (double) maxDim;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        return scaled;
    }

    private BufferedImage normalizeImage(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = rgb.createGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        return rgb;
    }
}
