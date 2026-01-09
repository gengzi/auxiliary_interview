package com.gengzi.backend.service;

import com.gengzi.backend.model.SolveRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * 面试助手服务
 * 提供文本问答和图片分析两种功能，支持流式和非流式返回
 */
@Service
public class SolveService {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String imagePrompt;

    /**
     * 构造函数，从classpath加载提示词模板
     */
    public SolveService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
        this.systemPrompt = loadPrompt("prompt/system-prompt.txt");
        this.imagePrompt = loadPrompt("prompt/image-prompt.txt");
    }

    /**
     * 处理文本面试问题（非流式）
     * @param request 包含文本问题的请求
     * @return LLM生成的答案
     */
    public String solve(SolveRequest request) {
        String text = request.getText();
        if (text == null || text.trim().isEmpty()) {
            return "错误：未提供问题内容";
        }

        return chatClient.prompt()
                .system(systemPrompt)
                .user(text)
                .call()
                .content();
    }

    /**
     * 处理文本面试问题（流式）
     * @param request 包含文本问题的请求
     * @param emitter SSE发射器，用于流式返回数据
     */
    public void solveStream(SolveRequest request, SseEmitter emitter) {
        String text = request.getText();
        if (text == null || text.trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().data("错误：未提供问题内容"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return;
        }

        try {
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(text)
                    .stream()
                    .content()
                    .forEach(chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 处理包含图片的面试问题（非流式）
     * @param base64Image Base64编码的图片数据
     * @param question 可选的附加问题，如果为空则只分析图片
     * @return LLM分析图片后生成的答案
     */
    public String solveWithImage(String base64Image, String question) {
        if (!StringUtils.hasText(base64Image)) {
            return "错误：未提供图片数据";
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        String userQuestion = StringUtils.hasText(question)
            ? imagePrompt + "\n\n用户附加问题：" + question
            : imagePrompt + "\n\n请直接分析图片中的面试问题并给出答案。";

        return chatClient.prompt()
                .system(imagePrompt)
                .user(userQuestion)
                .user(userSpec -> userSpec.media(MimeType.valueOf("image/png"), new ByteArrayResource(imageBytes)))
                .call()
                .content();
    }

    /**
     * 处理包含图片的面试问题（流式）
     * @param base64Image Base64编码的图片数据
     * @param question 可选的附加问题，如果为空则只分析图片
     * @param emitter SSE发射器，用于流式返回数据
     */
    public void solveWithImageStream(String base64Image, String question, SseEmitter emitter) {
        if (!StringUtils.hasText(base64Image)) {
            try {
                emitter.send(SseEmitter.event().data("错误：未提供图片数据"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            String userQuestion = StringUtils.hasText(question)
                ? imagePrompt + "\n\n用户附加问题：" + question
                : imagePrompt + "\n\n请直接分析图片中的面试问题并给出答案。";

            chatClient.prompt()
                    .system(imagePrompt)
                    .user(userQuestion)
                    .user(userSpec -> userSpec.media(MimeType.valueOf("image/png"), new ByteArrayResource(imageBytes)))
                    .stream()
                    .content()
                    .forEach(chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 从classpath加载提示词文件
     * @param path 提示词文件路径（相对于classpath）
     * @return 提示词内容，如果加载失败返回默认提示词
     */
    private String loadPrompt(String path) {
        try {
            // 使用Spring的ResourceLoader从classpath加载资源
            var resource = new org.springframework.core.io.ClassPathResource(path);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // 加载失败，返回默认提示词
        }
        return getDefaultPrompt(path.contains("image"));
    }

    /**
     * 获取默认提示词（当外部文件加载失败时使用）
     */
    private String getDefaultPrompt(boolean isImagePrompt) {
        if (isImagePrompt) {
            return "你是一位专业的面试助手。请分析图片中的面试问题并提供准确答案。";
        }
        return "你是一位专业的面试助手。请提供简洁、准确的答案。如果需要代码，请提供完整可运行的示例。";
    }
}
