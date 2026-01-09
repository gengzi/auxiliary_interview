package com.gengzi.backend.controller;

import com.gengzi.backend.model.TranscriptionRequest;
import com.gengzi.backend.model.TranscriptionResponse;
import com.gengzi.backend.service.TranscriptionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 语音转文字控制器
 * 提供音频转录的 REST API
 */
@RestController
@RequestMapping("/api")
public class TranscriptionController {
    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    /**
     * 语音转文字接口
     *
     * @param request 转录请求，包含 Base64 编码的音频数据和格式
     * @return 转录响应，包含识别出的文字
     *
     * 请求示例:
     * {
     *   "audioData": "base64_encoded_audio_data",
     *   "format": "mp3",
     *   "language": "zh",  // 可选，默认 auto 自动检测
     *   "prompt": "面试问题" // 可选，用于指导转录
     * }
     *
     * 响应示例:
     * {
     *   "text": "识别出的文字内容",
     *   "language": "zh",
     *   "duration": 1500
     * }
     */
    @PostMapping("/transcribe")
    public TranscriptionResponse transcribe(@RequestBody TranscriptionRequest request) {
        return transcriptionService.transcribe(request);
    }

    /**
     * 快速语音转文字接口（简化版）
     *
     * @param request 简化的请求体，只需包含 audioData 和 format
     * @return 转录后的文本
     *
     * 请求示例:
     * {
     *   "audioData": "base64_encoded_audio_data",
     *   "format": "wav"
     * }
     */
    @PostMapping("/transcribe-quick")
    public String transcribeQuick(@RequestBody TranscriptionRequest request) {
        return transcriptionService.transcribeQuick(
            request.getAudioData(),
            request.getFormat()
        );
    }
}
