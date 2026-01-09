package com.gengzi.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.backend.model.TranscriptionRequest;
import com.gengzi.backend.model.TranscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * 语音转文字服务
 * 直接调用 SiliconFlow 的语音转文字 API
 * 支持模型: FunAudioLLM/SenseVoiceSmall, TeleAI/TeleSpeechASR
 */
@Service
public class TranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TranscriptionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将 Base64 编码的音频转换为文字
     *
     * @param request 转录请求，包含音频数据和配置
     * @return 转录响应，包含文本结果
     */
    public TranscriptionResponse transcribe(TranscriptionRequest request) {
        log.info("开始语音转文字");
        log.info("音频格式: {}, 语言: {}", request.getFormat(), request.getLanguage());

        // 验证请求
        if (request.getAudioData() == null || request.getAudioData().trim().isEmpty()) {
            log.warn("音频数据为空");
            return new TranscriptionResponse("", "error", 0L);
        }

        if (request.getFormat() == null || request.getFormat().trim().isEmpty()) {
            log.warn("音频格式为空，默认使用 mp3");
            request.setFormat("mp3");
        }

        try {
            long startTime = System.currentTimeMillis();

            // 解码 Base64 音频数据
            byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
            log.info("音频数据解码成功，字节长度: {}", audioBytes.length);

            // 构建 multipart/form-data 请求
            String url = baseUrl + "/v1/audio/transcriptions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            // 创建 multipart body
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 添加音频文件
            HttpEntity<byte[]> audioPart = new HttpEntity<>(audioBytes, createAudioHeaders(request.getFormat()));
            body.add("file", audioPart);

            // 添加模型参数 - 使用 SenseVoiceSmall 模型
            body.add("model", "FunAudioLLM/SenseVoiceSmall");

            log.info("调用 SiliconFlow 语音转文字 API，模型: FunAudioLLM/SenseVoiceSmall");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
            );

            long duration = System.currentTimeMillis() - startTime;

            // 解析响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String resultText = jsonResponse.get("text").asText();

                log.info("转录完成，耗时: {}ms, 文本长度: {}", duration, resultText.length());
                log.debug("转录文本: {}", resultText);

                return new TranscriptionResponse(resultText, request.getLanguage(), duration);
            } else {
                log.error("API 返回错误状态码: {}", response.getStatusCode());
                return new TranscriptionResponse(
                    "错误：API 返回状态码 " + response.getStatusCode(),
                    "error",
                    duration
                );
            }

        } catch (Exception e) {
            log.error("语音转文字失败", e);
            return new TranscriptionResponse(
                "错误：转录失败 - " + e.getMessage(),
                "error",
                0L
            );
        }
    }

    /**
     * 快速转录方法（使用默认配置）
     *
     * @param base64Audio Base64 编码的音频数据
     * @param format 音频格式
     * @return 转录后的文本
     */
    public String transcribeQuick(String base64Audio, String format) {
        TranscriptionRequest request = new TranscriptionRequest(base64Audio, format);
        request.setLanguage("auto");
        TranscriptionResponse response = transcribe(request);
        return response.getText();
    }

    /**
     * 创建音频文件的 Content-Type header
     */
    private HttpHeaders createAudioHeaders(String format) {
        HttpHeaders headers = new HttpHeaders();
        String contentType = switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "mp4", "m4a" -> "audio/mp4";
            case "webm" -> "audio/webm";
            default -> "audio/mpeg";
        };
        headers.setContentType(MediaType.parseMediaType(contentType));
        return headers;
    }
}
