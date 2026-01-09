package com.gengzi.desktop.ocr;

import com.gengzi.desktop.llm.BackendClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * OCR服务（实际使用LLM进行图片分析）
 * 支持流式和非流式两种模式
 */
public class OcrService {
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageData = baos.toByteArray();

        return backendClient.solveWithImage(imageData, "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.");
    }

    /**
     * 分析图片并流式返回答案（流式）
     * @param image 截图
     * @param chunkConsumer 接收每个文本块的回调函数
     */
    public void recognizeStream(BufferedImage image, Consumer<String> chunkConsumer) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageData = baos.toByteArray();

        backendClient.solveWithImageStream(imageData, "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.", chunkConsumer);
    }
}
