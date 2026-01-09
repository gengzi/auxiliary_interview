package com.gengzi.backend.controller;

import com.gengzi.backend.model.SolveRequest;
import com.gengzi.backend.model.SolveResponse;
import com.gengzi.backend.service.SolveService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 面试助手控制器
 * 提供文本问答和图片分析的REST API，支持流式和非流式返回
 */
@RestController
@RequestMapping("/api")
public class SolveController {
    private final SolveService solveService;

    public SolveController(SolveService solveService) {
        this.solveService = solveService;
    }

    /**
     * 文本问答接口（非流式）
     */
    @PostMapping("/solve")
    public SolveResponse solve(@RequestBody SolveRequest request) {
        String answer = solveService.solve(request);
        return new SolveResponse(answer);
    }

    /**
     * 文本问答接口（流式，SSE）
     */
    @PostMapping(value = "/solve-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter solveStream(@RequestBody SolveRequest request) {
        // Create SSE emitter with a longer timeout for LLM responses.
        SseEmitter emitter = new SseEmitter(180000L);

        // 异步执行流式处理
        CompletableFuture.runAsync(() -> {
            try {
                solveService.solveStream(request, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 图片分析接口（非流式）
     */
    @PostMapping("/solve-image")
    public SolveResponse solveWithImage(@RequestBody Map<String, String> request) {
        String image = request.get("image");
        String question = request.get("question");
        String answer = solveService.solveWithImage(image, question);
        return new SolveResponse(answer);
    }

    /**
     * 图片分析接口（流式，SSE）
     */
    @PostMapping(value = "/solve-image-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter solveWithImageStream(@RequestBody Map<String, String> request) {
        // Create SSE emitter with a longer timeout for image analysis.
        SseEmitter emitter = new SseEmitter(240000L);

        // 异步执行流式处理
        CompletableFuture.runAsync(() -> {
            try {
                String image = request.get("image");
                String question = request.get("question");
                solveService.solveWithImageStream(image, question, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
