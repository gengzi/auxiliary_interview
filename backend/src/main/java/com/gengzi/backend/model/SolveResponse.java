package com.gengzi.backend.model;

public class SolveResponse {
    private String answer;

    public SolveResponse() {
    }

    public SolveResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
