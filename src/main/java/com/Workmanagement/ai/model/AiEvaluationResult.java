package com.Workmanagement.ai.model;

import java.util.List;

public class AiEvaluationResult {

    private Double completionPercentage;
    private Double qualityScore;
    private Double confidenceScore;

    private String feedback;

    private List<RequirementEvaluation> requirements;

    public AiEvaluationResult() {
    }

    public Double getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(Double completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public List<RequirementEvaluation> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<RequirementEvaluation> requirements) {
        this.requirements = requirements;
    }
}