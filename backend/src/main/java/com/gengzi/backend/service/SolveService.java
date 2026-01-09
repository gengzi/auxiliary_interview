package com.gengzi.backend.service;

import com.gengzi.backend.model.SolveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(SolveService.class);
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String imagePrompt;

    /**
     * 构造函数，从classpath加载提示词模板
     */
    public SolveService(ChatClient.Builder builder) {
        log.info("初始化 SolveService");
        this.chatClient = builder.build();
        this.systemPrompt = loadPrompt("prompt/system-prompt.txt");
        this.imagePrompt = loadPrompt("prompt/image-prompt.txt");
        log.info("systemPrompt 长度: {}, imagePrompt 长度: {}",
                 systemPrompt.length(), imagePrompt.length());
    }

    /**
     * 处理文本面试问题（非流式）
     * @param request 包含文本问题的请求
     * @return LLM生成的答案
     */
    public String solve(SolveRequest request) {
        log.info("开始处理文本请求（非流式）");
        String text = request.getText();
        log.info("请求内容长度: {}", text != null ? text.length() : 0);

        if (text == null || text.trim().isEmpty()) {
            log.warn("请求内容为空");
            return "错误：未提供问题内容";
        }

        log.info("调用 LLM API（非流式）");
        long startTime = System.currentTimeMillis();
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(text)
                .call()
                .content();
        long duration = System.currentTimeMillis() - startTime;

        log.info("LLM 响应完成，耗时: {}ms, 响应长度: {}", duration, response.length());
        return response;
    }

    /**
     * 处理文本面试问题（流式）
     * @param request 包含文本问题的请求
     * @param emitter SSE发射器，用于流式返回数据
     */
    public void solveStream(SolveRequest request, SseEmitter emitter) {
        log.info("开始处理文本请求（流式）");
        String text = request.getText();
        log.info("请求内容长度: {}", text != null ? text.length() : 0);

        if (text == null || text.trim().isEmpty()) {
            log.warn("请求内容为空");
            try {
                emitter.send(SseEmitter.event().data("错误：未提供问题内容"));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
                emitter.completeWithError(e);
            }
            return;
        }

        try {
            log.info("调用 LLM API（流式）");
            long startTime = System.currentTimeMillis();
            final int[] chunkCount = {0};

            chatClient.prompt()
                    .system(systemPrompt)
                    .user(text)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        chunkCount[0]++;
                        log.debug("收到流式数据块 #{}: 长度={}, 内容={}",
                                 chunkCount[0], chunk.length(),
                                 chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.error("发送流式数据失败", e);
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnComplete(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("流式响应完成，总块数: {}, 耗时: {}ms", chunkCount[0], duration);
                        emitter.complete();
                    })
                    .doOnError(error -> {
                        log.error("流式响应发生错误", error);
                        emitter.completeWithError(error);
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("流式处理异常", e);
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
        log.info("开始处理图片请求（非流式）");
        log.info("图片数据长度: {}, 附加问题: {}",
                 base64Image != null ? base64Image.length() : 0,
                 StringUtils.hasText(question) ? question : "无");

        if (!StringUtils.hasText(base64Image)) {
            log.warn("图片数据为空");
            return "错误：未提供图片数据";
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            log.info("图片解码成功，字节长度: {}", imageBytes.length);

            String userQuestion = StringUtils.hasText(question)
                ? imagePrompt + "\n\n用户附加问题：" + question
                : imagePrompt + "\n\n请直接分析图片中的面试问题并给出答案。";

            log.info("调用 LLM API（非流式，带图片）");
            long startTime = System.currentTimeMillis();
            String mimeType = detectImageMimeType(imageBytes);
            String response = chatClient.prompt()
                    .system(imagePrompt)
                    .user(userQuestion)
                    .user(userSpec -> userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes)))
                    .call()
                    .content();
            long duration = System.currentTimeMillis() - startTime;

            log.info("LLM 图片分析完成，耗时: {}ms, 响应长度: {}", duration, response.length());
            return response;
        } catch (Exception e) {
            log.error("图片处理失败", e);
            return "错误：图片处理失败 - " + e.getMessage();
        }
    }

    /**
     * 处理包含图片的面试问题（流式）
     * @param base64Image Base64编码的图片数据
     * @param question 可选的附加问题，如果为空则只分析图片
     * @param emitter SSE发射器，用于流式返回数据
     */
    public void solveWithImageStream(String base64Image, String question, SseEmitter emitter) {
        log.info("开始处理图片请求（流式）");
        log.info("图片数据长度: {}, 附加问题: {}",
                 base64Image != null ? base64Image.length() : 0,
                 StringUtils.hasText(question) ? question : "无");

        if (!StringUtils.hasText(base64Image)) {
            log.warn("图片数据为空");
            try {
                emitter.send(SseEmitter.event().data("错误：未提供图片数据"));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
                emitter.completeWithError(e);
            }
            return;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            log.info("图片解码成功，字节长度: {}", imageBytes.length);

            String userQuestion = StringUtils.hasText(question)
                ? imagePrompt + "\n\n用户附加问题：" + question
                : imagePrompt + "\n\n请直接分析图片中的面试问题并给出答案。";

            log.info("调用 LLM API（流式，带图片）");
            long startTime = System.currentTimeMillis();
            final int[] chunkCount = {0};
            String mimeType = detectImageMimeType(imageBytes);

            chatClient.prompt()
                    .system(imagePrompt)
                    .user(userQuestion)
                    .user(userSpec -> userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes)))
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        chunkCount[0]++;
                        log.info("收到流式数据块 #{}: 长度={}, 内容={}",
                                 chunkCount[0], chunk.length(),
                                 chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.error("发送流式数据失败", e);
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnComplete(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("流式响应完成（带图片），总块数: {}, 耗时: {}ms", chunkCount[0], duration);
                        emitter.complete();
                    })
                    .doOnError(error -> {
                        log.error("流式响应发生错误", error);
                        emitter.completeWithError(error);
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("图片流式处理异常", e);
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
            log.info("加载提示词文件: {}", path);
            // 使用Spring的ResourceLoader从classpath加载资源
            var resource = new org.springframework.core.io.ClassPathResource(path);
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                log.info("提示词文件加载成功，长度: {}", content.length());
                return content;
            } else {
                log.warn("提示词文件不存在: {}, 使用默认提示词", path);
            }
        } catch (IOException e) {
            log.error("加载提示词文件失败: {}, 使用默认提示词", path, e);
        }
        return getDefaultPrompt(path.contains("image"));
    }

    private String detectImageMimeType(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 4) {
            return "image/png";
        }
        int b0 = imageBytes[0] & 0xFF;
        int b1 = imageBytes[1] & 0xFF;
        int b2 = imageBytes[2] & 0xFF;
        int b3 = imageBytes[3] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8) {
            return "image/jpeg";
        }
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "image/png";
        }
        return "image/png";
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
