package com.Workmanagement.dashboard.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.repository.AiEvaluationRepository;

import com.Workmanagement.dashboard.model.DashboardTaskItem;
import com.Workmanagement.dashboard.model.EmployeeDashboardResponse;
import com.Workmanagement.dashboard.model.EmployeeSubmissionItem;
import com.Workmanagement.dashboard.model.ManagerDashboardResponse;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates real database values for manager and employee dashboards.
 * Returns clean DTOs only - never entity graphs.
 */
@Service
public class DashboardService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final AiEvaluationRepository aiEvaluationRepository;

    public DashboardService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            AiEvaluationRepository aiEvaluationRepository) {

        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
    }

    // =========================================================
    // MANAGER DASHBOARD
    // =========================================================

    @Transactional(readOnly = true)
    public ManagerDashboardResponse getManagerDashboard() {

        List<Task> tasks = taskRepository.findAll();
        LocalDate today = LocalDate.now();

        long totalTasks = tasks.size();

        long completedTasks = count(tasks, t -> t.getStatus() == TaskStatus.COMPLETED);
        long pendingTasks = count(tasks, t ->
                t.getStatus() == TaskStatus.CREATED
                        || t.getStatus() == TaskStatus.ASSIGNED
                        || t.getStatus() == TaskStatus.IN_PROGRESS);
        long submittedTasks = count(tasks, t ->
                t.getStatus() == TaskStatus.SUBMITTED
                        || t.getStatus() == TaskStatus.AI_EVALUATING
                        || t.getStatus() == TaskStatus.UNDER_REVIEW
                        || t.getStatus() == TaskStatus.CHANGES_REQUESTED);
        long overdueTasks = count(tasks, t ->
                t.getEndDate() != null
                        && t.getEndDate().isBefore(today)
                        && t.getStatus() != TaskStatus.COMPLETED);
        long underReviewTasks = count(tasks, t ->
                t.getStatus() == TaskStatus.UNDER_REVIEW);
        long awaitingAiEvaluationTasks = count(tasks, t ->
                t.getStatus() == TaskStatus.AI_EVALUATING
                        || t.getStatus() == TaskStatus.SUBMITTED);
        long approvedTasks = count(tasks, t ->
                t.getStatus() == TaskStatus.APPROVED);

        long totalEmployees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE)
                .count();

        List<AiEvaluation> evaluations = aiEvaluationRepository.findAll();

        Double avgCompletion = average(
                evaluations.stream()
                        .map(AiEvaluation::getCompletionPercentage)
                        .filter(java.util.Objects::nonNull)
                        .toList());

        Double avgQuality = average(
                evaluations.stream()
                        .map(AiEvaluation::getQualityScore)
                        .filter(java.util.Objects::nonNull)
                        .toList());

        // High risk: reuse the same rule logic as WorkRiskService
        // (overdue / due today / due within 2 days and not completed).
        long highRiskTasks = count(tasks, t -> {
            if (t.getStatus() == TaskStatus.COMPLETED || t.getEndDate() == null) {
                return false;
            }
            long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, t.getEndDate());
            return daysRemaining <= 0;
        });

        return ManagerDashboardResponse.builder()
                .totalProjects(projectRepository.count())
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .submittedTasks(submittedTasks)
                .overdueTasks(overdueTasks)
                .underReviewTasks(underReviewTasks)
                .awaitingAiEvaluationTasks(awaitingAiEvaluationTasks)
                .approvedTasks(approvedTasks)
                .totalEmployees(totalEmployees)
                .totalSubmissions(submissionRepository.count())
                .averageAiCompletionPercentage(round(avgCompletion))
                .averageAiQualityScore(round(avgQuality))
                .highRiskTasks(highRiskTasks)
                .build();
    }

    // =========================================================
    // EMPLOYEE DASHBOARD (authenticated user only)
    // =========================================================

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse getEmployeeDashboard(String email) {

        User employee = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        List<Task> tasks = taskRepository.findByAssignedToId(employee.getId());
        List<Submission> submissions = submissionRepository.findBySubmittedById(employee.getId());

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(7);

        long inProgressTasks = count(tasks, t -> t.getStatus() == TaskStatus.IN_PROGRESS);
        long completedTasks = count(tasks, t -> t.getStatus() == TaskStatus.COMPLETED);
        long changesRequestedTasks = count(tasks, t -> t.getStatus() == TaskStatus.CHANGES_REQUESTED);

        long pendingSubmissions = submissions.stream()
                .filter(s -> s.getStatus() != SubmissionStatus.APPROVED)
                .count();

        List<Task> upcoming = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .filter(t -> t.getEndDate() != null)
                .filter(t -> !t.getEndDate().isBefore(today) && t.getEndDate().isBefore(soon))
                .sorted(Comparator.comparing(Task::getEndDate))
                .toList();

        List<DashboardTaskItem> taskItems = tasks.stream()
                .sorted(Comparator.comparing(Task::getId))
                .map(this::toTaskItem)
                .toList();

        List<DashboardTaskItem> upcomingItems = upcoming.stream()
                .map(this::toTaskItem)
                .toList();

        List<EmployeeSubmissionItem> submissionItems = submissions.stream()
                .sorted(Comparator.comparing(Submission::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(this::toSubmissionItem)
                .toList();

        return EmployeeDashboardResponse.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .assignedTasks(tasks.size())
                .inProgressTasks(inProgressTasks)
                .completedTasks(completedTasks)
                .pendingSubmissions(pendingSubmissions)
                .changesRequestedTasks(changesRequestedTasks)
                .upcomingDeadlineTasks(upcoming.size())
                .tasks(taskItems)
                .upcomingDeadlines(upcomingItems)
                .recentSubmissions(submissionItems)
                .build();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private DashboardTaskItem toTaskItem(Task task) {
        Project project = task.getProject();
        return DashboardTaskItem.builder()
                .taskId(task.getId())
                .title(task.getTitle())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority())
                .deadline(task.getEndDate())
                .projectId(project != null ? project.getId() : null)
                .projectName(project != null ? project.getName() : null)
                .createdAt(task.getCreatedAt())
                .build();
    }

    private EmployeeSubmissionItem toSubmissionItem(Submission submission) {

        Optional<AiEvaluation> evaluation =
                aiEvaluationRepository.findBySubmissionId(submission.getId());

        Task task = submission.getTask();

        return EmployeeSubmissionItem.builder()
                .submissionId(submission.getId())
                .taskId(task != null ? task.getId() : null)
                .taskTitle(task != null ? task.getTitle() : null)
                .status(submission.getStatus() != null ? submission.getStatus().name() : null)
                .submittedAt(submission.getSubmittedAt())
                .aiCompletionPercentage(evaluation.map(AiEvaluation::getCompletionPercentage).orElse(null))
                .aiQualityScore(evaluation.map(AiEvaluation::getQualityScore).orElse(null))
                .aiFeedback(evaluation.map(AiEvaluation::getFeedback).orElse(null))
                .build();
    }

    private long count(List<Task> tasks, java.util.function.Predicate<Task> predicate) {
        return tasks.stream().filter(predicate).count();
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return sum / values.size();
    }

    private Double round(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
