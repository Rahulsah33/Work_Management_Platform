package com.Workmanagement.submission.entity;

import com.Workmanagement.task.entity.Task;
import com.Workmanagement.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_submission_id")
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

    public Submission getPreviousSubmission() {
        return previousSubmission;
    }

    public void setPreviousSubmission(Submission previousSubmission) {
        this.previousSubmission = previousSubmission;
    }
}