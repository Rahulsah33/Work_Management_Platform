package com.Workmanagement.submission.entity;

import com.Workmanagement.task.entity.Task;
import com.Workmanagement.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "submissions",
        indexes = {
                @Index(name = "idx_submissions_task_id", columnList = "task_id"),
                @Index(name = "idx_submissions_submitted_by", columnList = "submitted_by"),
                @Index(name = "idx_submissions_status", columnList = "status"),
                @Index(name = "idx_submissions_submitted_at", columnList = "submittedAt")
        }
)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5000)
    private String report;

    @Column(length = 2000)
    private String githubUrl;

    @Column(length = 2000)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    @Column(nullable = false)
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    @JsonIgnoreProperties({"submissions", "requirements", "hibernateLazyInitializer", "handler"})
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    private User submittedBy;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(length = 5000)
    private String managerFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_submission_id")
    @JsonIgnoreProperties({"previousSubmission", "task", "hibernateLazyInitializer", "handler"})
    private Submission previousSubmission;

    public Submission() {
    }

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = SubmissionStatus.SUBMITTED;
        }

        if (version == null) {
            version = 1;
        }
    }

    public Long getId() {
        return id;
    }

    public String getReport() {
        return report;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Task getTask() {
        return task;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public void setEvidenceUrl(String evidenceUrl) {
        this.evidenceUrl = evidenceUrl;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public void setSubmittedBy(User submittedBy) {
        this.submittedBy = submittedBy;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getManagerFeedback() {
        return managerFeedback;
    }

    public void setManagerFeedback(String managerFeedback) {
        this.managerFeedback = managerFeedback;
    }

    public Submission getPreviousSubmission() {
        return previousSubmission;
    }

    public void setPreviousSubmission(Submission previousSubmission) {
        this.previousSubmission = previousSubmission;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}