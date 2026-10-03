package com.Workmanagement.dashboard.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.dashboard.model.EmployeeDashboardResponse;
import com.Workmanagement.dashboard.model.EmployeeSubmissionSummary;
import com.Workmanagement.dashboard.model.EmployeeTaskStatistics;
import com.Workmanagement.dashboard.model.EmployeeTaskSummary;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmployeeDashboardService {

    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UserRepository userRepository;

    public EmployeeDashboardService(
            TaskRepository taskRepository,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            UserRepository userRepository
    ) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse getDashboard() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        String email = authentication.getName();

        User employee = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Authenticated user not found"));

        if (employee.getRole() != Role.EMPLOYEE && employee.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Access denied: User is not an employee or admin");
        }

        LocalDate today = LocalDate.now();

        // 1. Retrieve employee tasks
        List<Task> tasks = taskRepository.findByAssignedToId(employee.getId());

        // 2. Retrieve employee submissions
        List<Submission> submissions = submissionRepository.findBySubmittedById(employee.getId());

        // 3. Map AI evaluations for employee submissions
        Map<Long, AiEvaluation> completedEvaluationsBySubmissionId = new HashMap<>();
        List<AiEvaluation> completedEvaluations = new ArrayList<>();

        for (Submission submission : submissions) {
            if (submission.getId() != null) {
                aiEvaluationRepository.findBySubmissionId(submission.getId())
                        .filter(evaluation -> evaluation.getStatus() == AiEvaluationStatus.COMPLETED)
                        .ifPresent(evaluation -> {
                            completedEvaluationsBySubmissionId.put(submission.getId(), evaluation);
                            completedEvaluations.add(evaluation);
                        });
            }
        }

        // 4. Calculate Task Statistics
        long totalTasks = tasks.size();
        long completedTasks = countTasks(tasks, TaskStatus.COMPLETED);
        long inProgressTasks = countTasks(tasks, TaskStatus.IN_PROGRESS);
        long submittedTasks = countTasks(tasks, TaskStatus.SUBMITTED);
        long underReviewTasks = countTasks(tasks, TaskStatus.UNDER_REVIEW);
        long changesRequestedTasks = countTasks(tasks, TaskStatus.CHANGES_REQUESTED);
        long overdueTasks = countOverdueTasks(tasks, today);

        EmployeeTaskStatistics taskStatistics = new EmployeeTaskStatistics(
                totalTasks,
                completedTasks,
                inProgressTasks,
                submittedTasks,
                underReviewTasks,
                changesRequestedTasks,
                overdueTasks
        );

        // 5. Calculate Submission Statistics
        long totalSubmissions = submissions.size();
        long approvedSubmissions = countSubmissions(submissions, SubmissionStatus.APPROVED);
        long changesRequestedSubmissions = countSubmissions(submissions, SubmissionStatus.CHANGES_REQUESTED);

        // 6. Calculate AI Averages (from completed evaluations only)
        double averageCompletionPercentage = roundToTwoDecimals(
                calculateAverage(completedEvaluations, AiEvaluation::getCompletionPercentage)
        );
        double averageQualityScore = roundToTwoDecimals(
                calculateAverage(completedEvaluations, AiEvaluation::getQualityScore)
        );
        double averageConfidenceScore = roundToTwoDecimals(
                calculateAverage(completedEvaluations, AiEvaluation::getConfidenceScore)
        );

        // 7. Build Task Summaries (sorted: overdue first, then nearest deadline)
        List<EmployeeTaskSummary> taskSummaries = tasks.stream()
                .map(task -> {
                    boolean overdue = isTaskOverdue(task, today);
                    String projectName = task.getProject() != null ? task.getProject().getName() : null;
                    String status = task.getStatus() != null ? task.getStatus().name() : null;

                    return new EmployeeTaskSummary(
                            task.getId(),
                            task.getTitle(),
                            projectName,
                            status,
                            task.getPriority(),
                            task.getEndDate(),
                            overdue
                    );
                })
                .sorted(
                        Comparator.comparing(EmployeeTaskSummary::overdue, Comparator.reverseOrder())
                                .thenComparing(EmployeeTaskSummary::endDate, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(EmployeeTaskSummary::taskId, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .collect(Collectors.toList());

        // 8. Build Recent Submission Summaries (newest first, max 10)
        List<EmployeeSubmissionSummary> recentSubmissions = submissions.stream()
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(submission -> {
                    AiEvaluation evaluation = completedEvaluationsBySubmissionId.get(submission.getId());
                    Double completionPct = evaluation != null ? evaluation.getCompletionPercentage() : null;
                    Double quality = evaluation != null ? evaluation.getQualityScore() : null;
                    Double confidence = evaluation != null ? evaluation.getConfidenceScore() : null;

                    Long taskId = submission.getTask() != null ? submission.getTask().getId() : null;
                    String taskTitle = submission.getTask() != null ? submission.getTask().getTitle() : null;
                    String status = submission.getStatus() != null ? submission.getStatus().name() : null;

                    return new EmployeeSubmissionSummary(
                            submission.getId(),
                            taskId,
                            taskTitle,
                            status,
                            submission.getSubmittedAt(),
                            completionPct,
                            quality,
                            confidence
                    );
                })
                .collect(Collectors.toList());

        // 9. Build and Return Response
        return new EmployeeDashboardResponse(
                totalTasks,
                completedTasks,
                inProgressTasks,
                submittedTasks,
                underReviewTasks,
                changesRequestedTasks,
                overdueTasks,

                totalSubmissions,
                approvedSubmissions,
                changesRequestedSubmissions,

                averageCompletionPercentage,
                averageQualityScore,
                averageConfidenceScore,

                taskStatistics,
                taskSummaries,
                recentSubmissions
        );
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private long countTasks(List<Task> tasks, TaskStatus status) {
        return tasks.stream()
                .filter(task -> task.getStatus() == status)
                .count();
    }

    private boolean isTaskOverdue(Task task, LocalDate today) {
        return task.getEndDate() != null
                && task.getEndDate().isBefore(today)
                && task.getStatus() != TaskStatus.COMPLETED;
    }

    private long countOverdueTasks(List<Task> tasks, LocalDate today) {
        return tasks.stream()
                .filter(task -> isTaskOverdue(task, today))
                .count();
    }

    private long countSubmissions(List<Submission> submissions, SubmissionStatus status) {
        return submissions.stream()
                .filter(submission -> submission.getStatus() == status)
                .count();
    }

    private double calculateAverage(
            List<AiEvaluation> evaluations,
            Function<AiEvaluation, Double> valueExtractor
    ) {
        return evaluations.stream()
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
