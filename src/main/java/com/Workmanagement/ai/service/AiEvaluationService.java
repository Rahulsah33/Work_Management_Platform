package com.Workmanagement.ai.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiEvaluationService {

    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final AiPromptService aiPromptService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public AiEvaluationService(
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            AiPromptService aiPromptService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService) {

        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.aiPromptService = aiPromptService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }


    // =========================================================
    // CREATE MANUAL EVALUATION
    // =========================================================

    @Transactional
    public AiEvaluation createEvaluation(
            Long submissionId,
            AiEvaluation evaluation) {

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Submission not found with id: " + submissionId
                        )
                );

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageSubmission(submission, currentUser);

        evaluation.setSubmission(submission);

        AiEvaluation saved = aiEvaluationRepository.save(evaluation);

        auditLogService.createLog(
                currentUser,
                AuditAction.AI_EVALUATION_CREATED,
                "AI_EVALUATION",
                saved.getId(),
                "Created AI evaluation for submission ID: " + submissionId
        );

        return saved;
    }


    // =========================================================
    // AI EVALUATION
    // =========================================================

    @Transactional
    public AiEvaluation evaluateSubmission(Long submissionId) {

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Submission not found with id: " + submissionId
                        )
                );

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageSubmission(submission, currentUser);

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Only submitted submissions can be evaluated"
            );
        }

        var task = submission.getTask();

        String taskDescription =
                task != null ? task.getDescription() : "";

        String employeeReport =
                submission.getReport();

        String requirements =
                task != null && task.getRequirements() != null
                        ? buildRequirements(task.getRequirements())
                        : "";

        // Call Gemini through Spring AI.
        AiEvaluationResult result =
                aiPromptService.evaluationResult(
                    taskDescription,
                    requirements,
                    employeeReport
                );

        // Create or update evaluation
        AiEvaluation evaluation = aiEvaluationRepository
                .findBySubmissionId(submissionId)
                .orElseGet(AiEvaluation::new);

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
        evaluation.setEvaluatedAt(LocalDateTime.now());

        // Only advance the submission and task after Gemini returns a valid result.
        submission.setStatus(SubmissionStatus.UNDER_REVIEW);
        if (task != null) {
            task.setStatus(TaskStatus.UNDER_REVIEW);
        }
        submissionRepository.save(submission);

        AiEvaluation savedEvaluation = aiEvaluationRepository.save(evaluation);

        if (task != null && task.getProject() != null && task.getProject().getManager() != null) {
            notificationService.createNotification(
                    task.getProject().getManager().getId(),
                    "AI Evaluation Ready",
                    "The AI evaluation for \"" + task.getTitle() + "\" is ready for manager review.",
                    NotificationType.AI_EVALUATION_READY,
                    "SUBMISSION",
                    submission.getId()
            );
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.AI_EVALUATION_COMPLETED,
                "AI_EVALUATION",
                savedEvaluation.getId(),
                "Completed AI evaluation for submission ID: " + submissionId
        );

        return savedEvaluation;
    }


    // =========================================================
    // GET EVALUATION BY ID
    // =========================================================

    public AiEvaluation getEvaluationById(Long id) {

        AiEvaluation evaluation = aiEvaluationRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "AiEvaluation not found with id: " + id
                        )
                );

        currentUserService.getCurrentUserOptional().ifPresent(user -> {
            if (evaluation.getSubmission() != null) {
                authorizationService.checkAccessSubmission(evaluation.getSubmission(), user);
            }
        });

        return evaluation;
    }


    // =========================================================
    // GET EVALUATION BY SUBMISSION
    // =========================================================

    public AiEvaluation getEvaluationsBySubmission(
            Long submissionId) {

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Submission not found with id: " + submissionId
                        )
                );

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessSubmission(submission, user));

        return aiEvaluationRepository
                .findBySubmissionId(submissionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
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

        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.MANAGER) {
            return aiEvaluationRepository.findByStatus(status)
                    .stream()
                    .filter(eval -> eval.getSubmission() != null && eval.getSubmission().getTask() != null
                            && eval.getSubmission().getTask().getProject() != null
                            && authorizationService.canManageProject(eval.getSubmission().getTask().getProject(), currentUser))
                    .toList();
        }

        return aiEvaluationRepository
                .findByStatus(status);
    }


    // =========================================================
    // UPDATE EVALUATION
    // =========================================================

    @Transactional
    public AiEvaluation updateEvaluation(
            Long id,
            AiEvaluation updatedEvaluation) {

        AiEvaluation existingEvaluation =
                aiEvaluationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "AiEvaluation not found with id: " + id
                                )
                        );

        User currentUser = currentUserService.getCurrentUser();
        if (existingEvaluation.getSubmission() != null) {
            authorizationService.checkManageSubmission(existingEvaluation.getSubmission(), currentUser);
        }

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

        AiEvaluation saved = aiEvaluationRepository.save(
                existingEvaluation
        );

        auditLogService.createLog(
                currentUser,
                AuditAction.AI_EVALUATION_UPDATED,
                "AI_EVALUATION",
                saved.getId(),
                "Updated AI evaluation ID: " + id
        );

        return saved;
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