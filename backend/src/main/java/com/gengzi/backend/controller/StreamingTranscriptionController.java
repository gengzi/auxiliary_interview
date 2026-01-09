package com.gengzi.backend.controller;

import com.gengzi.backend.model.StreamingTranscriptionRequest;
import com.gengzi.backend.service.StreamingTranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 流式语音转文字控制器
 * 提供实时语音识别的 REST API
 */
@RestController
@RequestMapping("/api")
public class StreamingTranscriptionController {
    private static final Logger log = LoggerFactory.getLogger(StreamingTranscriptionController.class);

    private final StreamingTranscriptionService streamingTranscriptionService;

    public StreamingTranscriptionController(StreamingTranscriptionService streamingTranscriptionService) {
        this.streamingTranscriptionService = streamingTranscriptionService;
    }

    /**
     * 开始流式语音识别会话
     *
     * @return SSE 发射器，用于推送识别结果
     *
     * 返回的会话 ID 用于后续的音频数据上传
     */
    @PostMapping(value = "/transcribe-stream/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startStream() {
        // 生成唯一会话 ID
        String sessionId = UUID.randomUUID().toString();
        log.info("创建新的流式识别会话: {}", sessionId);

        // 创建 SSE 发射器，超时时间 5 分钟
        SseEmitter emitter = new SseEmitter(300000L);

        // 异步启动会话
        CompletableFuture.runAsync(() -> {
            try {
                streamingTranscriptionService.startSession(sessionId, emitter);

                // 发送会话 ID
                emitter.send(SseEmitter.event()
                    .name("session-id")
                    .data(Map.of("sessionId", sessionId)));

            } catch (Exception e) {
                log.error("启动流式识别会话失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 发送音频数据块
     *
     * @param request 包含音频数据块和会话 ID
     * @return 操作结果
     *
     * 前端应该：
     * 1. 录制音频（例如每 1-2 秒）
     * 2. 将音频块转换为 Base64
     * 3. 发送到此接口，携带会话 ID
     * 4. 重复步骤 1-3 直到录制完成
     * 5. 调用 /transcribe-stream/end 接口结束会话
     */
    @PostMapping("/transcribe-stream/chunk")
    public Map<String, String> sendAudioChunk(@RequestBody StreamingTranscriptionRequest request) {
        log.debug("收到音频块，会话: {}, 大小: {}",
                 request.getSessionId(),
                 request.getAudioChunk() != null ? request.getAudioChunk().length() : 0);

        streamingTranscriptionService.processAudioChunk(request);

        return Map.of(
            "status", "success",
            "sessionId", request.getSessionId()
        );
    }

    /**
     * 手动触发识别（不等待更多音频数据）
     *
     * @param requestBody 包含会话 ID 和音频格式
     * @return 操作结果
     */
    @PostMapping("/transcribe-stream/trigger")
    public Map<String, String> triggerTranscription(@RequestBody Map<String, String> requestBody) {
        String sessionId = requestBody.get("sessionId");
        String format = requestBody.getOrDefault("format", "wav");

        log.info("手动触发识别，会话: {}", sessionId);

        streamingTranscriptionService.triggerTranscription(sessionId, format);

        return Map.of(
            "status", "success",
            "message", "识别已触发"
        );
    }

    /**
     * 结束流式识别会话
     *
     * @param requestBody 包含会话 ID
     * @return 操作结果
     */
    @PostMapping("/transcribe-stream/end")
    public Map<String, String> endStream(@RequestBody Map<String, String> requestBody) {
        String sessionId = requestBody.get("sessionId");

        log.info("结束流式识别会话: {}", sessionId);

        streamingTranscriptionService.endSession(sessionId);

        return Map.of(
            "status", "success",
            "message", "会话已结束"
        );
    }

    /**
     * 完整的流式识别接口（单个接口处理整个流程）
     * 使用 SSE 返回识别结果
     *
     * @return SSE 发射器
     *
     * 使用流程：
     * 1. 前端连接此接口获取 SSE 连接和会话 ID
     * 2. 使用返回的会话 ID 调用 /transcribe-stream/chunk 发送音频
     * 3. 实时接收识别结果
     * 4. 完成后调用 /transcribe-stream/end
     */
    @PostMapping(value = "/transcribe-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter transcribeStream() {
        return startStream();
    }
}
