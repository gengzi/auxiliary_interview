package com.gengzi.backend.model;

/**
 * 语音转文字响应
 */
public class TranscriptionResponse {
    /**
     * 转录后的文本内容
     */
    private String text;

    /**
     * 识别的语言（可选）
     */
    private String language;

    /**
     * 处理时长（毫秒）
     */
    private Long duration;

    public TranscriptionResponse() {
    }

    public TranscriptionResponse(String text) {
        this.text = text;
    }

    public TranscriptionResponse(String text, String language, Long duration) {
        this.text = text;
        this.language = language;
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }
}
