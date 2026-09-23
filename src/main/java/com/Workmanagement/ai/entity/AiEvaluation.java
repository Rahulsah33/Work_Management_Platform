package com.Workmanagement.ai.entity;

import com.Workmanagement.submission.entity.Submission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_evaluations")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AiEvaluation {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double completionPercentage;

    @Column(nullable = false)
    private Double qualityScore;

    @Column(length = 5000)
    private String feedback;

    @Column(length = 5000)
    private String missingRequirements;

    @Column(length = 5000)
    private String partialRequirements;

    @Column(nullable = false)
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiEvaluationStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id" , nullable = false, unique = true)
    private Submission submission;

    @Column(nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = AiEvaluationStatus.PENDING;
        }
    }
}

