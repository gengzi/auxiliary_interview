package com.gengzi.backend.service;

import com.gengzi.backend.model.StreamingTranscriptionRequest;
import com.gengzi.backend.model.StreamingTranscriptionResponse;
import com.gengzi.backend.model.TranscriptionRequest;
import com.gengzi.backend.model.TranscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实时流式语音转文字服务
 * 通过音频缓冲和定时识别实现准实时语音识别
 */
@Service
public class StreamingTranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(StreamingTranscriptionService.class);

    private final TranscriptionService transcriptionService;
    private final ScheduledExecutorService scheduler;

    // 存储每个会话的音频缓冲区
    private final ConcurrentHashMap<String, ByteArrayOutputStream> audioBuffers;

    // 存储每个会话的最后识别结果
    private final ConcurrentHashMap<String, String> lastResults;

    // 存储每个会话的 SSE 发射器
    private final ConcurrentHashMap<String, SseEmitter> emitters;

    // 音频缓冲时间（毫秒），超过此时间自动触发识别
    private static final long BUFFER_TIMEOUT_MS = 3000; // 3秒

    public StreamingTranscriptionService(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
        this.audioBuffers = new ConcurrentHashMap<>();
        this.lastResults = new ConcurrentHashMap<>();
        this.emitters = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);

        log.info("StreamingTranscriptionService 初始化完成，缓冲时间: {}ms", BUFFER_TIMEOUT_MS);
    }

    /**
     * 开始一个新的流式识别会话
     *
     * @param sessionId 会话 ID
     * @param emitter SSE 发射器
     */
    public void startSession(String sessionId, SseEmitter emitter) {
        log.info("开始新的流式识别会话: {}", sessionId);

        audioBuffers.put(sessionId, new ByteArrayOutputStream());
        lastResults.put(sessionId, "");
        emitters.put(sessionId, emitter);

        // 发送会话开始事件
        sendEvent(sessionId, new StreamingTranscriptionResponse(
            "会话已开始，请发送音频数据...",
            false,
            sessionId
        ));
    }

    /**
     * 处理音频数据块
     *
     * @param request 包含音频数据块的请求
     */
    public void processAudioChunk(StreamingTranscriptionRequest request) {
        String sessionId = request.getSessionId();
        log.debug("收到音频块，会话: {}, 大小: {}, 是否最后: {}",
                 sessionId,
                 request.getAudioChunk() != null ? request.getAudioChunk().length() : 0,
                 request.isFinal());

        ByteArrayOutputStream buffer = audioBuffers.get(sessionId);
        SseEmitter emitter = emitters.get(sessionId);

        if (buffer == null || emitter == null) {
            log.warn("会话不存在: {}", sessionId);
            return;
        }

        try {
            // 解码并添加音频数据到缓冲区
            byte[] audioChunk = Base64.getDecoder().decode(request.getAudioChunk());
            buffer.write(audioChunk);

            log.debug("音频缓冲区大小: {} bytes", buffer.size());

            // 如果是最后一个块，立即进行识别
            if (request.isFinal()) {
                log.info("收到最后音频块，开始识别，会话: {}", sessionId);
                performTranscription(sessionId, request.getFormat());
            }

        } catch (Exception e) {
            log.error("处理音频块失败，会话: {}", sessionId, e);
            sendError(sessionId, "处理音频块失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发识别（不等待缓冲区超时）
     *
     * @param sessionId 会话 ID
     * @param format 音频格式
     */
    public void triggerTranscription(String sessionId, String format) {
        log.info("手动触发识别，会话: {}", sessionId);
        performTranscription(sessionId, format);
    }

    /**
     * 执行语音识别
     *
     * @param sessionId 会话 ID
     * @param format 音频格式
     */
    private void performTranscription(String sessionId, String format) {
        ByteArrayOutputStream buffer = audioBuffers.get(sessionId);
        if (buffer == null || buffer.size() == 0) {
            log.warn("缓冲区为空，无法识别，会话: {}", sessionId);
            return;
        }

        try {
            byte[] audioData = buffer.toByteArray();
            String base64Audio = Base64.getEncoder().encodeToString(audioData);

            log.info("开始识别，会话: {}, 音频大小: {} bytes", sessionId, audioData.length);

            // 调用转录服务
            TranscriptionRequest request = new TranscriptionRequest(base64Audio, format);
            TranscriptionResponse response = transcriptionService.transcribe(request);

            if (response.getText() != null && !response.getText().isEmpty()) {
                // 发送识别结果
                StreamingTranscriptionResponse streamingResponse =
                    new StreamingTranscriptionResponse(
                        response.getText(),
                        true,
                        sessionId
                    );

                sendEvent(sessionId, streamingResponse);

                // 更新最后结果
                lastResults.put(sessionId, response.getText());

                log.info("识别完成，会话: {}, 文本: {}", sessionId, response.getText());
            } else {
                log.warn("识别结果为空，会话: {}", sessionId);
            }

            // 清空缓冲区
            buffer.reset();
            log.debug("缓冲区已清空，会话: {}", sessionId);

        } catch (Exception e) {
            log.error("识别失败，会话: {}", sessionId, e);
            sendError(sessionId, "识别失败: " + e.getMessage());
        }
    }

    /**
     * 结束会话
     *
     * @param sessionId 会话 ID
     */
    public void endSession(String sessionId) {
        log.info("结束会话: {}", sessionId);

        ByteArrayOutputStream buffer = audioBuffers.get(sessionId);
        SseEmitter emitter = emitters.get(sessionId);

        if (buffer != null && buffer.size() > 0) {
            // 如果缓冲区还有数据，先进行最后一次识别
            log.info("会话结束，执行最后识别，会话: {}", sessionId);
            // 格式默认为 wav，实际应该从请求中获取
            performTranscription(sessionId, "wav");
        }

        // 清理资源
        audioBuffers.remove(sessionId);
        lastResults.remove(sessionId);
        emitters.remove(sessionId);

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .data(new StreamingTranscriptionResponse("会话已结束", true, sessionId)));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送会话结束事件失败", e);
            }
        }

        log.info("会话已清理: {}", sessionId);
    }

    /**
     * 发送事件到客户端
     */
    private void sendEvent(String sessionId, StreamingTranscriptionResponse response) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(response));
            } catch (Exception e) {
                log.error("发送事件失败，会话: {}", sessionId, e);
            }
        }
    }

    /**
     * 发送错误信息
     */
    private void sendError(String sessionId, String errorMessage) {
        StreamingTranscriptionResponse response = new StreamingTranscriptionResponse();
        response.setText("错误: " + errorMessage);
        response.setSessionId(sessionId);
        response.setFinal(true);
        sendEvent(sessionId, response);
    }

    /**
     * 关闭服务，清理资源
     */
    public void shutdown() {
        log.info("关闭 StreamingTranscriptionService");
        scheduler.shutdown();
        audioBuffers.clear();
        lastResults.clear();
        emitters.clear();
    }
}
