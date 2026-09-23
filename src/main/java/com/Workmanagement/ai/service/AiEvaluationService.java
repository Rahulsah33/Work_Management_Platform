package com.Workmanagement.ai.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiEvaluationService {

    private final AiEvaluationRepository aiEvaluationRepository;
    private final SubmissionRepository submissionRepository;

    public AiEvaluationService(
            AiEvaluationRepository aiEvaluationRepository,
            SubmissionRepository submissionRepository
    )
        {
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.submissionRepository = submissionRepository;
        }

        // Create AI evaluation
    public AiEvaluation createEvaluation(Long submissionId, AiEvaluation evaluation) {
        Submission submission = submissionRepository.findById(submissionId).orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        evaluation.setSubmission(submission);

        if (evaluation.getStatus() == null){
            evaluation.setStatus(AiEvaluationStatus.PENDING);
        }
        return aiEvaluationRepository.save(evaluation);
    }

    // Get evaluation by ID
    public AiEvaluation getEvaluationById(Long id) {
        return aiEvaluationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI Submission not found: " + id));
    }

    // Get Evaluation for a submission
    public  AiEvaluation getEvaluationBySubmission(Long submissionId) {
        return aiEvaluationRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException("AI evaluation not found"));
    }

    // Get Evaluation By status

    public List<AiEvaluation> getEvaluationsByStatus(AiEvaluationStatus status) {
        return aiEvaluationRepository.findByStatus(status);
    }

    // update evaluation
    public AiEvaluation updateEvaluation(Long id, AiEvaluation  updatedEvaluation) {

        AiEvaluation existingEvaluation = getEvaluationById(id);

        existingEvaluation.setCompletionPercentage(updatedEvaluation.getCompletionPercentage());

        existingEvaluation.setQualityScore(updatedEvaluation.getQualityScore());

        existingEvaluation.setFeedback(updatedEvaluation.getFeedback());

        existingEvaluation.setMissingRequirements(updatedEvaluation.getMissingRequirements());

        existingEvaluation.setPartialRequirements(updatedEvaluation.getPartialRequirements());

        existingEvaluation.setConfidenceScore(updatedEvaluation.getConfidenceScore());

        existingEvaluation.setStatus(updatedEvaluation.getStatus());

        return aiEvaluationRepository.save(existingEvaluation);
    }


}
