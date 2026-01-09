package com.gengzi.backend.model;

/**
 * 流式语音转文字响应
 */
public class StreamingTranscriptionResponse {
    /**
     * 识别出的文本片段
     */
    private String text;

    /**
     * 是否为最终结果
     */
    private boolean isFinal;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 识别进度（0-100）
     */
    private Double progress;

    public StreamingTranscriptionResponse() {
    }

    public StreamingTranscriptionResponse(String text, boolean isFinal, String sessionId) {
        this.text = text;
        this.isFinal = isFinal;
        this.sessionId = sessionId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Double getProgress() {
        return progress;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }
}
