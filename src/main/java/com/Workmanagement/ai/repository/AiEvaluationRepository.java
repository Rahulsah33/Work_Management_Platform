package com.Workmanagement.ai.repository;


import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    Optional<AiEvaluation> findBySubmissionId(Long submissionId);
    List<AiEvaluation> findByStatus(AiEvaluationStatus status);
}
