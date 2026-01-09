package com.gengzi.desktop.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 后端API客户端
 * 提供文本问答和图片分析功能，支持流式和非流式调用
 */
public class BackendClient {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public BackendClient(String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 文本问答（非流式）
     * @param text 问题文本
     * @return 完整答案
     */
    public String solve(String text) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);

        String json = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/solve"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<?, ?> map = mapper.readValue(response.body(), Map.class);
            Object answer = map.get("answer");
            return answer == null ? "" : answer.toString();
        }
        throw new IllegalStateException("Backend error: " + response.statusCode() + " " + response.body());
    }

    /**
     * 文本问答（流式）
     * @param text 问题文本
     * @param chunkConsumer 接收每个文本块的回调函数
     */
    public void solveStream(String text, Consumer<String> chunkConsumer) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);

        String json = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/solve-stream"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            parseSSEStream(response.body(), chunkConsumer);
        } else {
            throw new IllegalStateException("Backend error: " + response.statusCode());
        }
    }

    /**
     * 图片分析（非流式）
     * @param imageData 图片字节数据
     * @param question 可选的附加问题
     * @return 完整答案
     */
    public String solveWithImage(byte[] imageData, String question) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question);
        payload.put("image", Base64.getEncoder().encodeToString(imageData));

        String json = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/solve-image"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<?, ?> map = mapper.readValue(response.body(), Map.class);
            Object answer = map.get("answer");
            return answer == null ? "" : answer.toString();
        }
        throw new IllegalStateException("Backend error: " + response.statusCode() + " " + response.body());
    }

    /**
     * 图片分析（流式）
     * @param imageData 图片字节数据
     * @param question 可选的附加问题
     * @param chunkConsumer 接收每个文本块的回调函数
     */
    public void solveWithImageStream(byte[] imageData, String question, Consumer<String> chunkConsumer) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question);
        payload.put("image", Base64.getEncoder().encodeToString(imageData));

        String json = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/solve-image-stream"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            parseSSEStream(response.body(), chunkConsumer);
        } else {
            throw new IllegalStateException("Backend error: " + response.statusCode());
        }
    }

    /**
     * 解析SSE流数据
     * SSE格式: data: <content>\n\n
     */
    private void parseSSEStream(java.io.InputStream inputStream, Consumer<String> chunkConsumer) throws Exception {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder dataBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        chunkConsumer.accept(data);
                    }
                }
            }
        }
    }
}
