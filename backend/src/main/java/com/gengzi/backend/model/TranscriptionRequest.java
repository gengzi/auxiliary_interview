package com.gengzi.backend.model;

/**
 * 语音转文字请求
 */
public class TranscriptionRequest {
    /**
     * Base64 编码的音频数据
     */
    private String audioData;

    /**
     * 音频文件格式（如: mp3, mp4, mpeg, mpga, m4a, wav, webm）
     */
    private String format;

    /**
     * 语言代码（如: zh, en, auto）
     * 默认为 auto，自动检测语言
     */
    private String language;

    /**
     * 提示词，用于指导转录
     */
    private String prompt;

    public TranscriptionRequest() {
    }

    public TranscriptionRequest(String audioData, String format) {
        this.audioData = audioData;
        this.format = format;
        this.language = "auto";
    }

    public String getAudioData() {
        return audioData;
    }

    public void setAudioData(String audioData) {
        this.audioData = audioData;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
