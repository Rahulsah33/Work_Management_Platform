package com.Workmanagement.ai.entity;

public class WorkRisk {

    private Long taskId;
    private String taskTitle;
    private String riskLevel;
    private String reason;
    private long daysRemaining;

    public WorkRisk() {
    }

    public WorkRisk(
            Long taskId,
            String taskTitle,
            String riskLevel,
            String reason,
            long daysRemaining
    ) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.riskLevel = riskLevel;
        this.reason = reason;
        this.daysRemaining = daysRemaining;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(long daysRemaining) {
        this.daysRemaining = daysRemaining;
    }
}