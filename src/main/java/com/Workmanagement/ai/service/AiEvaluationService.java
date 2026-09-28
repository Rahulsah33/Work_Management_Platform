package com.Workmanagement.ai.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.Workmanagement.submission.entity.SubmissionStatus;

import java.util.List;

@Service
public class AiEvaluationService {

    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final AiPromptService aiPromptService;


    public AiEvaluationService(
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            AiPromptService aiPromptService) {

        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.aiPromptService = aiPromptService;
    }


    // =========================================================
    // CREATE MANUAL EVALUATION
    // =========================================================

    public AiEvaluation createEvaluation(
            Long submissionId,
            AiEvaluation evaluation) {

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Submission not found: " + submissionId
                        )
                );

        evaluation.setSubmission(submission);

        return aiEvaluationRepository.save(evaluation);
    }


    // =========================================================
    // AI EVALUATION
    // =========================================================

    @Transactional
    public AiEvaluation evaluateSubmission(Long submissionId) {

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Submission not found: " + submissionId
                        )
                );

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new RuntimeException(
                    "Only submitted submissions can be evaluated"
            );
        }

        var task = submission.getTask();

        String taskDescription =
                task.getDescription();

        String employeeReport =
                submission.getReport();

        String requirements =
                buildRequirements(
                        task.getRequirements()
                );

        // Call Gemini through Spring AI.
        AiEvaluationResult result =
                aiPromptService.evaluationResult(
                        taskDescription,
                        requirements,
                        employeeReport
                );

        // Create evaluation
        AiEvaluation evaluation =
                new AiEvaluation();

        evaluation.setSubmission(submission);

        evaluation.setCompletionPercentage(
                result.getCompletionPercentage()
        );

        evaluation.setQualityScore(
                result.getQualityScore()
        );

        evaluation.setConfidenceScore(
                result.getConfidenceScore()
        );

        evaluation.setFeedback(
                result.getFeedback()
        );

        evaluation.setMissingRequirements(
                buildMissingRequirements(result)
        );

        evaluation.setPartialRequirements(
                buildPartialRequirements(result)
        );

        evaluation.setStatus(
                AiEvaluationStatus.COMPLETED
        );

        // Only advance the submission after Gemini returns a valid result.
        submission.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(submission);

        return aiEvaluationRepository.save(evaluation);
    }


    // =========================================================
    // GET EVALUATION BY ID
    // =========================================================

    public AiEvaluation getEvaluationById(Long id) {

        return aiEvaluationRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Evaluation not found: " + id
                        )
                );
    }


    // =========================================================
    // GET EVALUATION BY SUBMISSION
    // =========================================================

    public AiEvaluation getEvaluationsBySubmission(
            Long submissionId) {

        return aiEvaluationRepository
                .findBySubmissionId(submissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Evaluation not found for submission: "
                                        + submissionId
                        )
                );
    }


    // =========================================================
    // GET EVALUATIONS BY STATUS
    // =========================================================

    public List<AiEvaluation> getEvaluationsByStatus(
            AiEvaluationStatus status) {

        return aiEvaluationRepository
                .findByStatus(status);
    }


    // =========================================================
    // UPDATE EVALUATION
    // =========================================================

    public AiEvaluation updateEvaluation(
            Long id,
            AiEvaluation updatedEvaluation) {

        AiEvaluation existingEvaluation =
                aiEvaluationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Evaluation not found: " + id
                                )
                        );

        existingEvaluation.setCompletionPercentage(
                updatedEvaluation.getCompletionPercentage()
        );

        existingEvaluation.setQualityScore(
                updatedEvaluation.getQualityScore()
        );

        existingEvaluation.setConfidenceScore(
                updatedEvaluation.getConfidenceScore()
        );

        existingEvaluation.setFeedback(
                updatedEvaluation.getFeedback()
        );

        existingEvaluation.setMissingRequirements(
                updatedEvaluation.getMissingRequirements()
        );

        existingEvaluation.setPartialRequirements(
                updatedEvaluation.getPartialRequirements()
        );

        if (updatedEvaluation.getStatus() != null) {
            existingEvaluation.setStatus(
                    updatedEvaluation.getStatus()
            );
        }

        return aiEvaluationRepository.save(
                existingEvaluation
        );
    }


    // =========================================================
    // BUILD REQUIREMENTS FOR AI
    // =========================================================

    private String buildRequirements(
            List<com.Workmanagement.task.entity.TaskRequirement> requirements) {

        StringBuilder builder = new StringBuilder();

        for (var requirement : requirements) {

            builder.append("ID: ")
                    .append(requirement.getId())
                    .append("\n");

            builder.append("Requirement: ")
                    .append(requirement.getDescription())
                    .append("\n");

            builder.append("Weight: ")
                    .append(requirement.getWeight())
                    .append("\n");

            builder.append("Mandatory: ")
                    .append(requirement.getMandatory())
                    .append("\n");

            builder.append("\n");
        }

        return builder.toString();
    }


    // =========================================================
    // BUILD MISSING REQUIREMENTS
    // =========================================================

    private String buildMissingRequirements(
            AiEvaluationResult result) {

        if (result.getRequirements() == null) {
            return "";
        }

        return result.getRequirements()
                .stream()
                .filter(r ->
                        "MISSING".equalsIgnoreCase(
                                r.getStatus()
                        )
                )
                .map(r ->
                        r.getRequirement()
                )
                .reduce(
                        "",
                        (a, b) ->
                                a.isEmpty()
                                        ? b
                                        : a + "\n" + b
                );
    }


    // =========================================================
    // BUILD PARTIAL REQUIREMENTS
    // =========================================================

    private String buildPartialRequirements(
            AiEvaluationResult result) {

        if (result.getRequirements() == null) {
            return "";
        }

        return result.getRequirements()
                .stream()
                .filter(r ->
                        "PARTIAL".equalsIgnoreCase(
                                r.getStatus()
                        )
                )
                .map(r ->
                        r.getRequirement()
                )
                .reduce(
                        "",
                        (a, b) ->
                                a.isEmpty()
                                        ? b
                                        : a + "\n" + b
                );
    }
}