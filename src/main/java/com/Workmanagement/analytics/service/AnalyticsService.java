package com.Workmanagement.analytics.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.analytics.model.*;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            UserRepository userRepository
    ) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // OVERVIEW ANALYTICS (Milestone 8 Step 1)
    // =========================================================

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverviewAnalytics(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));

        if (!isAdmin && !isManager) {
            throw new AccessDeniedException("Access denied: You do not have permission to view manager analytics overview");
        }

        String userEmail = auth.getName();

        // 1. Filter Projects based on authorization & date
        List<Project> scopedProjects = projectRepository.findAll().stream()
                .filter(p -> isAdmin || (p.getManager() != null && userEmail.equals(p.getManager().getEmail())))
                .filter(p -> isDateInRange(getProjectDate(p), startDate, endDate))
                .toList();

        Set<Long> projectIds = scopedProjects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. Filter Tasks based on scoped projects & date
        List<Task> scopedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectIds.contains(t.getProject().getId()))
                .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                .toList();

        Set<Long> taskIds = scopedTasks.stream()
                .map(Task::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. Filter Submissions based on scoped tasks & date
        List<Submission> scopedSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && taskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Set<Long> submissionIds = scopedSubmissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 4. Filter AI Evaluations based on scoped submissions & date
        List<AiEvaluation> scopedEvaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        // 5. Build Metric Summaries
        ProjectAnalyticsSummary projectSummary = buildProjectSummary(scopedProjects);
        TaskAnalyticsSummary taskSummary = buildTaskSummary(scopedTasks);
        SubmissionAnalyticsSummary submissionSummary = buildSubmissionSummary(scopedSubmissions);
        EvaluationAnalyticsSummary evaluationSummary = buildEvaluationSummary(scopedEvaluations);

        // 6. Calculate Top-Level Rates
        double completionRate = calculateCompletionRate(scopedTasks);
        double approvalRate = calculateApprovalRate(scopedSubmissions);

        String startStr = startDate != null ? startDate.toString() : null;
        String endStr = endDate != null ? endDate.toString() : null;

        return new AnalyticsOverviewResponse(
                projectSummary,
                taskSummary,
                submissionSummary,
                evaluationSummary,
                completionRate,
                approvalRate,
                startStr,
                endStr
        );
    }

    // =========================================================
    // EMPLOYEE PERFORMANCE ANALYTICS (Milestone 8 Step 2)
    // =========================================================

    @Transactional(readOnly = true)
    public List<EmployeePerformanceResponse> getEmployeesAnalytics(LocalDate startDate, LocalDate endDate, Long projectId) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));

        if (!isAdmin && !isManager) {
            throw new AccessDeniedException("Access denied: Employees cannot view employee list analytics");
        }

        String userEmail = auth.getName();

        // 1. Scoped projects
        List<Project> scopedProjects = projectRepository.findAll().stream()
                .filter(p -> isAdmin || (p.getManager() != null && userEmail.equals(p.getManager().getEmail())))
                .filter(p -> projectId == null || p.getId().equals(projectId))
                .toList();

        if (projectId != null && scopedProjects.isEmpty()) {
            if (!isAdmin) {
                projectRepository.findById(projectId).ifPresent(p -> {
                    throw new AccessDeniedException("You are not authorized to view analytics for project ID: " + projectId);
                });
            }
        }

        Set<Long> projectIds = scopedProjects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. Fetch tasks for scoped projects
        List<Task> allScopedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectIds.contains(t.getProject().getId()))
                .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                .toList();

        // Batch fetch submissions for all scoped tasks at once
        Set<Long> allTaskIds = allScopedTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && allTaskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Map<Long, List<Submission>> submissionsByTaskId = allSubmissions.stream()
                .filter(s -> s.getTask() != null && s.getTask().getId() != null)
                .collect(Collectors.groupingBy(s -> s.getTask().getId()));

        // Batch fetch evaluations for all scoped submissions at once
        Set<Long> allSubmissionIds = allSubmissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> allEvaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && allSubmissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        Map<Long, List<AiEvaluation>> evaluationsBySubmissionId = allEvaluations.stream()
                .filter(e -> e.getSubmission() != null && e.getSubmission().getId() != null)
                .collect(Collectors.groupingBy(e -> e.getSubmission().getId()));

        // Group tasks by assigned employee
        Map<User, List<Task>> tasksByEmployee = allScopedTasks.stream()
                .filter(t -> t.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Task::getAssignedTo));

        List<User> employees = tasksByEmployee.keySet().stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(User::getId))
                .toList();

        return employees.stream()
                .map(emp -> {
                    List<Task> empTasks = tasksByEmployee.getOrDefault(emp, List.of());
                    List<Submission> empSubmissions = empTasks.stream()
                            .flatMap(t -> submissionsByTaskId.getOrDefault(t.getId(), List.of()).stream())
                            .toList();
                    List<AiEvaluation> empEvaluations = empSubmissions.stream()
                            .flatMap(s -> evaluationsBySubmissionId.getOrDefault(s.getId(), List.of()).stream())
                            .toList();
                    return buildEmployeePerformance(emp, empTasks, empSubmissions, empEvaluations, startDate, endDate);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeePerformanceResponse getEmployeeAnalyticsById(Long employeeId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        User targetEmployee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
        boolean isEmployee = auth.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));

        String userEmail = auth.getName();

        // Check Employee self-access only
        if (isEmployee && !isAdmin && !isManager) {
            if (!targetEmployee.getEmail().equals(userEmail)) {
                throw new AccessDeniedException("Access denied: You can only view your own performance analytics");
            }
        }

        // Check Manager scope
        List<Project> managerProjects = List.of();
        if (isManager && !isAdmin) {
            managerProjects = projectRepository.findAll().stream()
                    .filter(p -> p.getManager() != null && userEmail.equals(p.getManager().getEmail()))
                    .toList();

            Set<Long> managerProjectIds = managerProjects.stream().map(Project::getId).collect(Collectors.toSet());
            boolean hasTaskInManagerProjects = taskRepository.findAll().stream()
                    .anyMatch(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId)
                            && t.getProject() != null && managerProjectIds.contains(t.getProject().getId()));

            if (!hasTaskInManagerProjects) {
                throw new AccessDeniedException("Access denied: You are not authorized to view analytics for this employee");
            }
        }

        // Fetch scoped tasks for this employee
        List<Task> employeeTasks;
        if (isManager && !isAdmin) {
            Set<Long> managerProjectIds = managerProjects.stream().map(Project::getId).collect(Collectors.toSet());
            employeeTasks = taskRepository.findAll().stream()
                    .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId))
                    .filter(t -> t.getProject() != null && managerProjectIds.contains(t.getProject().getId()))
                    .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                    .toList();
        } else {
            employeeTasks = taskRepository.findAll().stream()
                    .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId))
                    .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                    .toList();
        }

        Set<Long> taskIds = employeeTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> submissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && taskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Set<Long> submissionIds = submissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> evaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        return buildEmployeePerformance(targetEmployee, employeeTasks, submissions, evaluations, startDate, endDate);
    }

    private EmployeePerformanceResponse buildEmployeePerformance(
            User employee,
            List<Task> tasks,
            List<Submission> submissions,
            List<AiEvaluation> evaluations,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate today = LocalDate.now();

        long totalAssignedTasks = tasks.size();
        long completedTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.APPROVED)
                .count();
        long pendingTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.CREATED || t.getStatus() == TaskStatus.ASSIGNED)
                .count();
        long inProgressTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        long activeTasks = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();
        long overdueTasks = tasks.stream()
                .filter(t -> t.getEndDate() != null && t.getEndDate().isBefore(today))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();

        double completionRate = totalAssignedTasks > 0
                ? roundToTwoDecimals((completedTasks * 100.0) / totalAssignedTasks)
                : 0.0;

        long totalSubmissions = submissions.size();
        long approvedSubmissions = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .count();
        long changesRequested = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED)
                .count();
        long resubmissions = submissions.stream()
                .filter(s -> s.getVersion() != null && s.getVersion() > 1)
                .count();

        long totalReviewed = approvedSubmissions + changesRequested;
        double approvalRate = totalReviewed > 0
                ? roundToTwoDecimals((approvedSubmissions * 100.0) / totalReviewed)
                : 0.0;

        double avgCompletion = calculateAverage(evaluations, AiEvaluation::getCompletionPercentage);
        double avgQuality = calculateAverage(evaluations, AiEvaluation::getQualityScore);
        double avgConfidence = calculateAverage(evaluations, AiEvaluation::getConfidenceScore);

        String startStr = startDate != null ? startDate.toString() : null;
        String endStr = endDate != null ? endDate.toString() : null;

        return new EmployeePerformanceResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                totalAssignedTasks,
                completedTasks,
                pendingTasks,
                inProgressTasks,
                activeTasks,
                overdueTasks,
                completionRate,
                totalSubmissions,
                approvedSubmissions,
                changesRequested,
                resubmissions,
                approvalRate,
                avgCompletion,
                avgQuality,
                avgConfidence,
                startStr,
                endStr
        );
    }

    // =========================================================
    // PROJECT PERFORMANCE ANALYTICS (Milestone 8 Step 3)
    // =========================================================

    @Transactional(readOnly = true)
    public List<ProjectPerformanceResponse> getProjectsAnalytics(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));

        if (!isAdmin && !isManager) {
            throw new AccessDeniedException("Access denied: Employees cannot view project list analytics");
        }

        String userEmail = auth.getName();

        List<Project> scopedProjects = projectRepository.findAll().stream()
                .filter(p -> isAdmin || (p.getManager() != null && userEmail.equals(p.getManager().getEmail())))
                .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Project::getId))
                .toList();

        Set<Long> projectIds = scopedProjects.stream().map(Project::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Task> allScopedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectIds.contains(t.getProject().getId()))
                .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                .toList();

        Map<Long, List<Task>> tasksByProjectId = allScopedTasks.stream()
                .filter(t -> t.getProject() != null && t.getProject().getId() != null)
                .collect(Collectors.groupingBy(t -> t.getProject().getId()));

        // Batch fetch submissions for all scoped tasks at once
        Set<Long> allTaskIds = allScopedTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && allTaskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Map<Long, List<Submission>> submissionsByTaskId = allSubmissions.stream()
                .filter(s -> s.getTask() != null && s.getTask().getId() != null)
                .collect(Collectors.groupingBy(s -> s.getTask().getId()));

        // Batch fetch evaluations for all scoped submissions at once
        Set<Long> allSubmissionIds = allSubmissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> allEvaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && allSubmissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        Map<Long, List<AiEvaluation>> evaluationsBySubmissionId = allEvaluations.stream()
                .filter(e -> e.getSubmission() != null && e.getSubmission().getId() != null)
                .collect(Collectors.groupingBy(e -> e.getSubmission().getId()));

        return scopedProjects.stream()
                .map(project -> {
                    List<Task> projTasks = tasksByProjectId.getOrDefault(project.getId(), List.of());
                    List<Submission> projSubmissions = projTasks.stream()
                            .flatMap(t -> submissionsByTaskId.getOrDefault(t.getId(), List.of()).stream())
                            .toList();
                    List<AiEvaluation> projEvaluations = projSubmissions.stream()
                            .flatMap(s -> evaluationsBySubmissionId.getOrDefault(s.getId(), List.of()).stream())
                            .toList();
                    return buildProjectPerformance(project, projTasks, projSubmissions, projEvaluations, startDate, endDate);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectPerformanceResponse getProjectAnalyticsById(Long projectId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
        boolean isEmployee = auth.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));

        String userEmail = auth.getName();

        if (isManager && !isAdmin) {
            if (project.getManager() == null || !userEmail.equals(project.getManager().getEmail())) {
                throw new AccessDeniedException("Access denied: You are not authorized to view analytics for this project");
            }
        } else if (isEmployee && !isAdmin) {
            boolean isAssignedToProject = taskRepository.findByProjectId(projectId).stream()
                    .anyMatch(t -> t.getAssignedTo() != null && userEmail.equals(t.getAssignedTo().getEmail()));

            if (!isAssignedToProject) {
                throw new AccessDeniedException("Access denied: You are not authorized to view analytics for this project");
            }
        } else if (!isAdmin && !isManager && !isEmployee) {
            throw new AccessDeniedException("Access denied: You do not have permission to view project analytics");
        }

        List<Task> projectTasks = taskRepository.findByProjectId(projectId).stream()
                .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                .toList();

        Set<Long> taskIds = projectTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> submissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && taskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Set<Long> submissionIds = submissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> evaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        return buildProjectPerformance(project, projectTasks, submissions, evaluations, startDate, endDate);
    }

    private ProjectPerformanceResponse buildProjectPerformance(
            Project project,
            List<Task> tasks,
            List<Submission> submissions,
            List<AiEvaluation> evaluations,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate today = LocalDate.now();

        long totalTasks = tasks.size();
        long completedTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.APPROVED)
                .count();
        long pendingTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.CREATED || t.getStatus() == TaskStatus.ASSIGNED)
                .count();
        long inProgressTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        long activeTasks = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();
        long overdueTasks = tasks.stream()
                .filter(t -> t.getEndDate() != null && t.getEndDate().isBefore(today))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();

        double completionRate = totalTasks > 0
                ? roundToTwoDecimals((completedTasks * 100.0) / totalTasks)
                : 0.0;

        long employeeCount = tasks.stream()
                .map(Task::getAssignedTo)
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .count();

        long totalSubmissions = submissions.size();
        long approvedSubmissions = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .count();
        long changesRequested = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED)
                .count();
        long resubmissions = submissions.stream()
                .filter(s -> s.getVersion() != null && s.getVersion() > 1)
                .count();

        long totalReviewed = approvedSubmissions + changesRequested;
        double approvalRate = totalReviewed > 0
                ? roundToTwoDecimals((approvedSubmissions * 100.0) / totalReviewed)
                : 0.0;

        double avgCompletion = calculateAverage(evaluations, AiEvaluation::getCompletionPercentage);
        double avgQuality = calculateAverage(evaluations, AiEvaluation::getQualityScore);
        double avgConfidence = calculateAverage(evaluations, AiEvaluation::getConfidenceScore);

        String startStr = startDate != null ? startDate.toString() : null;
        String endStr = endDate != null ? endDate.toString() : null;

        return new ProjectPerformanceResponse(
                project.getId(),
                project.getName(),
                totalTasks,
                completedTasks,
                pendingTasks,
                inProgressTasks,
                activeTasks,
                overdueTasks,
                completionRate,
                totalSubmissions,
                approvedSubmissions,
                changesRequested,
                resubmissions,
                approvalRate,
                avgCompletion,
                avgQuality,
                avgConfidence,
                employeeCount,
                startStr,
                endStr
        );
    }

    // =========================================================
    // TASK & PRODUCTIVITY ANALYTICS (Milestone 8 Step 4)
    // =========================================================

    @Transactional(readOnly = true)
    public List<TaskPerformanceResponse> getTasksAnalytics(
            LocalDate startDate,
            LocalDate endDate,
            Long projectId,
            Long employeeId,
            TaskStatus status
    ) {
        validateDateRange(startDate, endDate);

        List<Task> scopedTasks = filterScopedTasks(startDate, endDate, projectId, employeeId, status);

        Set<Long> taskIds = scopedTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && taskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Map<Long, List<Submission>> submissionsByTaskId = allSubmissions.stream()
                .collect(Collectors.groupingBy(s -> s.getTask().getId()));

        Set<Long> submissionIds = allSubmissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> allEvaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        Map<Long, List<AiEvaluation>> evaluationsByTaskId = allEvaluations.stream()
                .filter(e -> e.getSubmission() != null && e.getSubmission().getTask() != null)
                .collect(Collectors.groupingBy(e -> e.getSubmission().getTask().getId()));

        return scopedTasks.stream()
                .sorted(Comparator.comparing((Task t) -> t.getProject() != null ? t.getProject().getName() : "", String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Task::getId))
                .map(t -> buildTaskPerformance(
                        t,
                        submissionsByTaskId.getOrDefault(t.getId(), List.of()),
                        evaluationsByTaskId.getOrDefault(t.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskProductivitySummaryResponse getTaskProductivitySummary(
            LocalDate startDate,
            LocalDate endDate,
            Long projectId,
            Long employeeId,
            TaskStatus status
    ) {
        validateDateRange(startDate, endDate);

        List<Task> scopedTasks = filterScopedTasks(startDate, endDate, projectId, employeeId, status);

        Set<Long> taskIds = scopedTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getTask() != null && taskIds.contains(s.getTask().getId()))
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Set<Long> submissionIds = allSubmissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> allEvaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        LocalDate today = LocalDate.now();

        long totalTasks = scopedTasks.size();
        long completedTasks = scopedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.APPROVED)
                .count();
        long pendingTasks = scopedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.CREATED || t.getStatus() == TaskStatus.ASSIGNED)
                .count();
        long inProgressTasks = scopedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        long activeTasks = scopedTasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();
        long overdueTasks = scopedTasks.stream()
                .filter(t -> t.getEndDate() != null && t.getEndDate().isBefore(today))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();

        double completionRate = totalTasks > 0
                ? roundToTwoDecimals((completedTasks * 100.0) / totalTasks)
                : 0.0;

        long totalSubmissions = allSubmissions.size();
        long approvedSubmissions = allSubmissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .count();
        long changesRequested = allSubmissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED)
                .count();
        long resubmissions = allSubmissions.stream()
                .filter(s -> s.getVersion() != null && s.getVersion() > 1)
                .count();

        long totalReviewed = approvedSubmissions + changesRequested;
        double approvalRate = totalReviewed > 0
                ? roundToTwoDecimals((approvedSubmissions * 100.0) / totalReviewed)
                : 0.0;

        Double avgAiCompletion = allEvaluations.isEmpty() ? null : calculateAverage(allEvaluations, AiEvaluation::getCompletionPercentage);
        Double avgAiQuality = allEvaluations.isEmpty() ? null : calculateAverage(allEvaluations, AiEvaluation::getQualityScore);
        Double avgAiConfidence = allEvaluations.isEmpty() ? null : calculateAverage(allEvaluations, AiEvaluation::getConfidenceScore);

        List<Double> completionTimes = scopedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.APPROVED)
                .map(this::calculateTaskCompletionHours)
                .filter(Objects::nonNull)
                .toList();

        Double avgCompletionTimeHours = completionTimes.isEmpty()
                ? null
                : roundToTwoDecimals(completionTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        String startStr = startDate != null ? startDate.toString() : null;
        String endStr = endDate != null ? endDate.toString() : null;

        return new TaskProductivitySummaryResponse(
                totalTasks,
                completedTasks,
                pendingTasks,
                inProgressTasks,
                activeTasks,
                overdueTasks,
                completionRate,
                totalSubmissions,
                approvedSubmissions,
                changesRequested,
                resubmissions,
                approvalRate,
                avgAiCompletion,
                avgAiQuality,
                avgAiConfidence,
                avgCompletionTimeHours,
                startStr,
                endStr
        );
    }

    @Transactional(readOnly = true)
    public TaskPerformanceResponse getTaskAnalyticsById(Long taskId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Authentication auth = getAuthenticatedUser();

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
        boolean isEmployee = auth.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));

        String userEmail = auth.getName();

        if (isManager && !isAdmin) {
            if (task.getProject() == null || task.getProject().getManager() == null
                    || !userEmail.equals(task.getProject().getManager().getEmail())) {
                throw new AccessDeniedException("Access denied: You are not authorized to view analytics for this task");
            }
        } else if (isEmployee && !isAdmin) {
            if (task.getAssignedTo() == null || !userEmail.equals(task.getAssignedTo().getEmail())) {
                throw new AccessDeniedException("Access denied: You are not authorized to view analytics for this task");
            }
        } else if (!isAdmin && !isManager && !isEmployee) {
            throw new AccessDeniedException("Access denied: You do not have permission to view task analytics");
        }

        List<Submission> submissions = submissionRepository.findByTaskId(taskId).stream()
                .filter(s -> isDateInRange(getSubmissionDate(s), startDate, endDate))
                .toList();

        Set<Long> submissionIds = submissions.stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<AiEvaluation> evaluations = aiEvaluationRepository.findAll().stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .filter(e -> e.getSubmission() != null && submissionIds.contains(e.getSubmission().getId()))
                .filter(e -> isDateInRange(getEvaluationDate(e), startDate, endDate))
                .toList();

        return buildTaskPerformance(task, submissions, evaluations);
    }

    private List<Task> filterScopedTasks(
            LocalDate startDate,
            LocalDate endDate,
            Long projectId,
            Long employeeId,
            TaskStatus status
    ) {
        Authentication auth = getAuthenticatedUser();

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isManager = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
        boolean isEmployee = auth.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));

        String userEmail = auth.getName();

        if (isEmployee && !isAdmin && !isManager) {
            User currentUser = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (employeeId != null && !employeeId.equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You can only view your own task analytics");
            }

            if (projectId != null) {
                boolean isAssignedToProject = taskRepository.findByProjectId(projectId).stream()
                        .anyMatch(t -> t.getAssignedTo() != null && userEmail.equals(t.getAssignedTo().getEmail()));
                if (!isAssignedToProject) {
                    throw new AccessDeniedException("Access denied: You are not assigned to project ID: " + projectId);
                }
            }

            return taskRepository.findAll().stream()
                    .filter(t -> t.getAssignedTo() != null && userEmail.equals(t.getAssignedTo().getEmail()))
                    .filter(t -> projectId == null || (t.getProject() != null && t.getProject().getId().equals(projectId)))
                    .filter(t -> employeeId == null || (t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId)))
                    .filter(t -> status == null || t.getStatus() == status)
                    .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                    .toList();
        }

        if (isManager && !isAdmin) {
            List<Project> managerProjects = projectRepository.findAll().stream()
                    .filter(p -> p.getManager() != null && userEmail.equals(p.getManager().getEmail()))
                    .toList();

            Set<Long> managerProjectIds = managerProjects.stream().map(Project::getId).collect(Collectors.toSet());

            if (projectId != null && !managerProjectIds.contains(projectId)) {
                throw new AccessDeniedException("You are not authorized to view tasks for project ID: " + projectId);
            }

            if (employeeId != null) {
                boolean hasEmployeeInManagerProjects = taskRepository.findAll().stream()
                        .anyMatch(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId)
                                && t.getProject() != null && managerProjectIds.contains(t.getProject().getId()));
                if (!hasEmployeeInManagerProjects) {
                    throw new AccessDeniedException("You are not authorized to view tasks for employee ID: " + employeeId);
                }
            }

            return taskRepository.findAll().stream()
                    .filter(t -> t.getProject() != null && managerProjectIds.contains(t.getProject().getId()))
                    .filter(t -> projectId == null || (t.getProject() != null && t.getProject().getId().equals(projectId)))
                    .filter(t -> employeeId == null || (t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId)))
                    .filter(t -> status == null || t.getStatus() == status)
                    .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                    .toList();
        }

        // Admin: global access
        if (projectId != null && !projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        if (employeeId != null && !userRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }

        return taskRepository.findAll().stream()
                .filter(t -> projectId == null || (t.getProject() != null && t.getProject().getId().equals(projectId)))
                .filter(t -> employeeId == null || (t.getAssignedTo() != null && t.getAssignedTo().getId().equals(employeeId)))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> isDateInRange(getTaskDate(t), startDate, endDate))
                .toList();
    }

    private TaskPerformanceResponse buildTaskPerformance(
            Task task,
            List<Submission> submissions,
            List<AiEvaluation> evaluations
    ) {
        LocalDate today = LocalDate.now();

        boolean isOverdue = task.getEndDate() != null
                && task.getEndDate().isBefore(today)
                && task.getStatus() != TaskStatus.COMPLETED
                && task.getStatus() != TaskStatus.APPROVED;

        long totalSubmissions = submissions.size();
        long approvedSubmissions = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .count();
        long changesRequested = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED)
                .count();
        long resubmissions = submissions.stream()
                .filter(s -> s.getVersion() != null && s.getVersion() > 1)
                .count();

        long totalReviewed = approvedSubmissions + changesRequested;
        double approvalRate = totalReviewed > 0
                ? roundToTwoDecimals((approvedSubmissions * 100.0) / totalReviewed)
                : 0.0;

        AiEvaluation latestEvaluation = evaluations.stream()
                .filter(e -> e.getStatus() == AiEvaluationStatus.COMPLETED)
                .sorted(Comparator.comparing(AiEvaluation::getEvaluatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AiEvaluation::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null);

        Double aiCompletion = latestEvaluation != null && latestEvaluation.getCompletionPercentage() != null
                ? roundToTwoDecimals(latestEvaluation.getCompletionPercentage())
                : null;
        Double aiQuality = latestEvaluation != null && latestEvaluation.getQualityScore() != null
                ? roundToTwoDecimals(latestEvaluation.getQualityScore())
                : null;
        Double aiConfidence = latestEvaluation != null && latestEvaluation.getConfidenceScore() != null
                ? roundToTwoDecimals(latestEvaluation.getConfidenceScore())
                : null;

        Double completionHours = (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.APPROVED)
                ? calculateTaskCompletionHours(task)
                : null;

        String createdAtStr = task.getCreatedAt() != null ? task.getCreatedAt().toString() : null;

        return new TaskPerformanceResponse(
                task.getId(),
                task.getTitle(),
                task.getProject() != null ? task.getProject().getId() : null,
                task.getProject() != null ? task.getProject().getName() : null,
                task.getAssignedTo() != null ? task.getAssignedTo().getId() : null,
                task.getAssignedTo() != null ? task.getAssignedTo().getName() : null,
                task.getStatus(),
                isOverdue,
                totalSubmissions,
                approvedSubmissions,
                changesRequested,
                resubmissions,
                approvalRate,
                aiCompletion,
                aiQuality,
                aiConfidence,
                completionHours,
                createdAtStr,
                task.getEndDate()
        );
    }

    private Double calculateTaskCompletionHours(Task task) {
        if (task.getCreatedAt() == null) {
            return null;
        }
        java.time.LocalDateTime end = task.getUpdatedAt() != null ? task.getUpdatedAt() : java.time.LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(task.getCreatedAt(), end);
        long minutes = Math.max(0, duration.toMinutes());
        return roundToTwoDecimals(minutes / 60.0);
    }

    // =========================================================
    // VALIDATION & DATE EXTRACTION HELPERS
    // =========================================================

    private Authentication getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required to access analytics");
        }
        return auth;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date cannot be after end date: " + startDate + " > " + endDate);
        }
    }

    private boolean isDateInRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (date == null) {
            return true; // Include if date is unspecified
        }
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    private LocalDate getProjectDate(Project project) {
        if (project.getStartDate() != null) {
            return project.getStartDate();
        }
        if (project.getCreatedAt() != null) {
            return project.getCreatedAt().toLocalDate();
        }
        return null;
    }

    private LocalDate getTaskDate(Task task) {
        if (task.getCreatedAt() != null) {
            return task.getCreatedAt().toLocalDate();
        }
        if (task.getEndDate() != null) {
            return task.getEndDate();
        }
        return null;
    }

    private LocalDate getSubmissionDate(Submission submission) {
        if (submission.getSubmittedAt() != null) {
            return submission.getSubmittedAt().toLocalDate();
        }
        return null;
    }

    private LocalDate getEvaluationDate(AiEvaluation evaluation) {
        if (evaluation.getEvaluatedAt() != null) {
            return evaluation.getEvaluatedAt().toLocalDate();
        }
        return null;
    }

    // =========================================================
    // SUMMARY BUILDERS
    // =========================================================

    private ProjectAnalyticsSummary buildProjectSummary(List<Project> projects) {
        long total = projects.size();
        long planned = projects.stream().filter(p -> p.getStatus() == ProjectStatus.PLANNED || p.getStatus() == ProjectStatus.PENDING).count();
        long active = projects.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).count();
        long completed = projects.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count();
        long archived = projects.stream().filter(p -> p.getStatus() == ProjectStatus.ARCHIVED || p.getStatus() == ProjectStatus.ONHOLD || p.getStatus() == ProjectStatus.CANCELLED).count();

        return new ProjectAnalyticsSummary(total, planned, active, completed, archived);
    }

    private TaskAnalyticsSummary buildTaskSummary(List<Task> tasks) {
        LocalDate today = LocalDate.now();

        long total = tasks.size();
        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.CREATED || t.getStatus() == TaskStatus.ASSIGNED).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long submitted = tasks.stream().filter(t -> t.getStatus() == TaskStatus.SUBMITTED || t.getStatus() == TaskStatus.AI_EVALUATING).count();
        long underReview = tasks.stream().filter(t -> t.getStatus() == TaskStatus.UNDER_REVIEW).count();
        long approved = tasks.stream().filter(t -> t.getStatus() == TaskStatus.APPROVED).count();
        long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long changesRequested = tasks.stream().filter(t -> t.getStatus() == TaskStatus.CHANGES_REQUESTED).count();

        long overdue = tasks.stream()
                .filter(t -> t.getEndDate() != null && t.getEndDate().isBefore(today))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.APPROVED)
                .count();

        return new TaskAnalyticsSummary(
                total,
                pending,
                inProgress,
                submitted,
                underReview,
                approved,
                completed,
                changesRequested,
                overdue
        );
    }

    private SubmissionAnalyticsSummary buildSubmissionSummary(List<Submission> submissions) {
        long total = submissions.size();
        long submitted = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED).count();
        long aiEvaluating = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.AI_EVALUATING).count();
        long underReview = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.UNDER_REVIEW).count();
        long approved = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
        long changesRequested = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED).count();
        long reviewed = approved + changesRequested;

        return new SubmissionAnalyticsSummary(
                total,
                submitted,
                aiEvaluating,
                underReview,
                approved,
                changesRequested,
                reviewed
        );
    }

    private EvaluationAnalyticsSummary buildEvaluationSummary(List<AiEvaluation> evaluations) {
        long total = evaluations.size();
        double avgCompletion = calculateAverage(evaluations, AiEvaluation::getCompletionPercentage);
        double avgQuality = calculateAverage(evaluations, AiEvaluation::getQualityScore);
        double avgConfidence = calculateAverage(evaluations, AiEvaluation::getConfidenceScore);

        return new EvaluationAnalyticsSummary(
                total,
                avgCompletion,
                avgQuality,
                avgConfidence
        );
    }

    // =========================================================
    // RATE & AVERAGE CALCULATION WITH ROUNDING
    // =========================================================

    private double calculateCompletionRate(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return 0.0;
        }
        long completedOrApproved = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.APPROVED)
                .count();

        return roundToTwoDecimals((completedOrApproved * 100.0) / tasks.size());
    }

    private double calculateApprovalRate(List<Submission> submissions) {
        long approved = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .count();
        long changesRequested = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CHANGES_REQUESTED)
                .count();
        long totalReviewed = approved + changesRequested;

        if (totalReviewed == 0) {
            return 0.0;
        }
        return roundToTwoDecimals((approved * 100.0) / totalReviewed);
    }

    private double calculateAverage(List<AiEvaluation> evaluations, Function<AiEvaluation, Double> extractor) {
        if (evaluations.isEmpty()) {
            return 0.0;
        }
        return evaluations.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .map(this::roundToTwoDecimals)
                .findFirst()
                .orElse(0.0);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
