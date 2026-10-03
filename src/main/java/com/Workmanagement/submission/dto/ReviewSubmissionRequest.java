package com.Workmanagement.submission.dto;

public class ReviewSubmissionRequest {

    private String feedback;

    public ReviewSubmissionRequest() {
    }

    public ReviewSubmissionRequest(String feedback) {
        this.feedback = feedback;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
