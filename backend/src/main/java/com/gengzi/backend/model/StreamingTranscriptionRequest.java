package com.gengzi.backend.model;

/**
 * 流式语音转文字请求
 * 用于实时语音识别，支持音频分块上传
 */
public class StreamingTranscriptionRequest {
    /**
     * Base64 编码的音频数据块
     */
    private String audioChunk;

    /**
     * 音频格式（如: mp3, wav, webm）
     */
    private String format;

    /**
     * 会话 ID，用于标识同一语音会话
     */
    private String sessionId;

    /**
     * 是否为最后一个音频块
     */
    private boolean isFinal;

    /**
     * 语言代码（如: zh, en）
     */
    private String language;

    public StreamingTranscriptionRequest() {
    }

    public StreamingTranscriptionRequest(String audioChunk, String format, String sessionId) {
        this.audioChunk = audioChunk;
        this.format = format;
        this.sessionId = sessionId;
        this.isFinal = false;
    }

    public String getAudioChunk() {
        return audioChunk;
    }

    public void setAudioChunk(String audioChunk) {
        this.audioChunk = audioChunk;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
