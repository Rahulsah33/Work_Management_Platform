package com.Workmanagement.dashboard.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.dashboard.model.EmployeeWorkload;
import com.Workmanagement.dashboard.model.ManagerDashboardResponse;
import com.Workmanagement.dashboard.model.ProjectSummary;
import com.Workmanagement.dashboard.model.TaskStatistics;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
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
import org.springframework.security.access.AccessDeniedException;
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
public class ManagerDashboardService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public ManagerDashboardService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public ManagerDashboardResponse getDashboard() {

        User currentUser = currentUserService.getCurrentUser();

        // -------------------------------------------------
        // Managers only see projects they own; admins retain global visibility.
        // -------------------------------------------------

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        List<Project> projects = isAdmin
                ? projectRepository.findAll()
                : projectRepository.findByManagerId(currentUser.getId());

        // -------------------------------------------------
        // TASKS
        // -------------------------------------------------

        Set<Long> ownedProjectIds = projects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Task> tasks = isAdmin
                ? taskRepository.findAll()
                : taskRepository.findAll().stream()
                .filter(task -> task.getProject() != null
                        && ownedProjectIds.contains(task.getProject().getId()))
                .toList();

        Set<Long> taskIds = tasks.stream()
                .map(Task::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // -------------------------------------------------
        // SUBMISSIONS
        // -------------------------------------------------

        List<Submission> submissions = isAdmin
                ? submissionRepository.findAll()
                : submissionRepository.findAll().stream()
                .filter(submission -> submission.getTask() != null
                        && taskIds.contains(submission.getTask().getId()))
                .toList();

        Set<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // -------------------------------------------------
        // AI EVALUATIONS
        // -------------------------------------------------

        List<AiEvaluation> evaluations = aiEvaluationRepository.findAll()
                .stream()
                .filter(evaluation ->
                        evaluation.getStatus() == AiEvaluationStatus.COMPLETED
                )
                .filter(evaluation -> isAdmin
                        || (evaluation.getSubmission() != null
                        && submissionIds.contains(evaluation.getSubmission().getId())))
                .toList();

        LocalDate today = LocalDate.now();

        // -------------------------------------------------
        // TASK STATISTICS
        // -------------------------------------------------

        TaskStatistics taskStatistics =
                buildTaskStatistics(tasks, today);

        // -------------------------------------------------
        // TASKS GROUPED BY PROJECT
        // -------------------------------------------------

        Map<Long, List<Task>> tasksByProject =
                tasks.stream()
                        .filter(task -> task.getProject() != null)
                        .collect(Collectors.groupingBy(
                                task -> task.getProject().getId()
                        ));

        List<EmployeeWorkload> employeeWorkloads =
                buildEmployeeWorkloads(tasks, today, isAdmin);

        // -------------------------------------------------
        // PROJECT SUMMARIES
        // -------------------------------------------------

        List<ProjectSummary> projectSummaries =
                projects.stream()
                        .sorted(Comparator.comparing(Project::getId))
                        .map(project ->
                                buildProjectSummary(
                                        project,
                                        tasksByProject.getOrDefault(
                                                 project.getId(),
                                                 List.of()
                                        ),
                                        today
                                )
                        )
                        .toList();

        // -------------------------------------------------
        // PROJECT STATISTICS
        // -------------------------------------------------

        long plannedProjects =
                countProjects(projects, ProjectStatus.PLANNED);

        long activeProjects =
                countProjects(projects, ProjectStatus.IN_PROGRESS);

        long completedProjects =
                countProjects(projects, ProjectStatus.COMPLETED);

        long archivedProjects =
                countProjects(projects, ProjectStatus.ARCHIVED);

        // -------------------------------------------------
        // SUBMISSION STATISTICS
        // -------------------------------------------------

        SubmissionStatistics submissionStatistics =
                buildSubmissionStatistics(submissions);

        // -------------------------------------------------
        // AI AVERAGE STATISTICS
        // -------------------------------------------------

        AverageStatistics averageStatistics =
                buildAverageStatistics(evaluations);

        long pendingReviews = submissionStatistics.submitted()
                + submissionStatistics.underReview()
                + submissionStatistics.aiEvaluating();

        // -------------------------------------------------
        // FINAL DASHBOARD RESPONSE
        // -------------------------------------------------

        return new ManagerDashboardResponse(

                // Projects
                projects.size(),
                plannedProjects,
                activeProjects,
                completedProjects,
                archivedProjects,

                // Tasks
                taskStatistics.totalTasks(),
                taskStatistics.completedTasks(),
                taskStatistics.inProgressTasks(),
                taskStatistics.submittedTasks(),
                taskStatistics.underReviewTasks(),
                taskStatistics.overdueTasks(),

                // Employees
                employeeWorkloads.size(),

                // Submissions
                submissions.size(),
                submissionStatistics.submitted(),
                submissionStatistics.aiEvaluating(),
                submissionStatistics.underReview(),
                submissionStatistics.approved(),
                submissionStatistics.changesRequested(),
                pendingReviews,

                // AI statistics
                averageStatistics.completionPercentage(),
                averageStatistics.qualityScore(),
                averageStatistics.confidenceScore(),

                // Detailed data
                taskStatistics,
                employeeWorkloads,
                projectSummaries
        );
    }

    // =====================================================
    // PROJECT ACCESS
    // =====================================================

    private boolean canViewProject(
            Project project,
            User currentUser
    ) {
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER) {
            return true;
        }

        if (project.getManager() == null) {
            return true;
        }

        return (project.getManager().getId() != null && project.getManager().getId().equals(currentUser.getId()))
                || (project.getManager().getEmail() != null && project.getManager().getEmail().equalsIgnoreCase(currentUser.getEmail()));
    }

    // =====================================================
    // TASK STATISTICS
    // =====================================================

    private TaskStatistics buildTaskStatistics(
            List<Task> tasks,
            LocalDate today
    ) {

        return new TaskStatistics(
                tasks.size(),

                countTasks(tasks, TaskStatus.CREATED),

                countTasks(tasks, TaskStatus.ASSIGNED),

                countTasks(tasks, TaskStatus.IN_PROGRESS),

                countTasks(tasks, TaskStatus.SUBMITTED),

                countTasks(tasks, TaskStatus.AI_EVALUATING),

                countTasks(tasks, TaskStatus.UNDER_REVIEW),

                countTasks(tasks, TaskStatus.APPROVED),

                countTasks(tasks, TaskStatus.CHANGES_REQUESTED),

                countTasks(tasks, TaskStatus.COMPLETED),

                countOverdueTasks(tasks, today)
        );
    }

    // =====================================================
    // EMPLOYEE WORKLOAD
    // =====================================================

    private List<EmployeeWorkload> buildEmployeeWorkloads(
            List<Task> tasks,
            LocalDate today,
            boolean isAdmin
    ) {

        Map<Long, List<Task>> tasksByEmployee =
                tasks.stream()
                        .filter(task ->
                                task.getAssignedTo() != null
                                        && task.getAssignedTo().getId() != null
                        )
                        .collect(Collectors.groupingBy(
                                task -> task.getAssignedTo().getId()
                        ));

        return userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == Role.EMPLOYEE)
                .sorted(Comparator.comparing(User::getId))
                .map(employee -> {

                    List<Task> employeeTasks =
                            tasksByEmployee.getOrDefault(
                                    employee.getId(),
                                    List.of()
                            );

                    return new EmployeeWorkload(

                            employee.getId(),

                            employee.getName(),

                            employee.getEmail(),

                            employeeTasks.size(),

                            countTasks(
                                    employeeTasks,
                                    TaskStatus.COMPLETED
                            ),

                            countTasks(
                                    employeeTasks,
                                    TaskStatus.IN_PROGRESS
                            ),

                            countTasks(
                                    employeeTasks,
                                    TaskStatus.SUBMITTED
                            ),

                            countOverdueTasks(
                                    employeeTasks,
                                    today
                            )
                    );
                })
                .toList();
    }

    // =====================================================
    // PROJECT SUMMARY
    // =====================================================

    private ProjectSummary buildProjectSummary(
            Project project,
            List<Task> tasks,
            LocalDate today
    ) {

        return new ProjectSummary(

                project.getId(),

                project.getName(),

                project.getStatus() == null
                        ? null
                        : project.getStatus().name(),

                tasks.size(),

                countTasks(
                        tasks,
                        TaskStatus.COMPLETED
                ),

                countTasks(
                        tasks,
                        TaskStatus.IN_PROGRESS
                ),

                countOverdueTasks(
                        tasks,
                        today
                )
        );
    }

    // =====================================================
    // SUBMISSION STATISTICS
    // =====================================================

    private SubmissionStatistics buildSubmissionStatistics(
            List<Submission> submissions
    ) {

        return new SubmissionStatistics(

                countSubmissions(
                        submissions,
                        SubmissionStatus.SUBMITTED
                ),

                countSubmissions(
                        submissions,
                        SubmissionStatus.AI_EVALUATING
                ),

                countSubmissions(
                        submissions,
                        SubmissionStatus.UNDER_REVIEW
                ),

                countSubmissions(
                        submissions,
                        SubmissionStatus.APPROVED
                ),

                countSubmissions(
                        submissions,
                        SubmissionStatus.CHANGES_REQUESTED
                )
        );
    }

    // =====================================================
    // AI EVALUATION STATISTICS
    // =====================================================

    private AverageStatistics buildAverageStatistics(
            List<AiEvaluation> evaluations
    ) {

        return new AverageStatistics(

                average(
                        evaluations,
                        AiEvaluation::getCompletionPercentage
                ),

                average(
                        evaluations,
                        AiEvaluation::getQualityScore
                ),

                average(
                        evaluations,
                        AiEvaluation::getConfidenceScore
                )
        );
    }

    // =====================================================
    // COUNT PROJECTS
    // =====================================================

    private long countProjects(
            List<Project> projects,
            ProjectStatus status
    ) {

        return projects.stream()
                .filter(project -> project.getStatus() == status)
                .count();
    }

    // =====================================================
    // COUNT TASKS
    // =====================================================

    private long countTasks(
            List<Task> tasks,
            TaskStatus status
    ) {

        return tasks.stream()
                .filter(task -> task.getStatus() == status)
                .count();
    }

    // =====================================================
    // COUNT OVERDUE TASKS
    // =====================================================

    private long countOverdueTasks(
            List<Task> tasks,
            LocalDate today
    ) {

        return tasks.stream()
                .filter(task -> task.getEndDate() != null)
                .filter(task ->
                        task.getEndDate().isBefore(today)
                )
                .filter(task ->
                        task.getStatus() != TaskStatus.COMPLETED
                )
                .count();
    }

    // =====================================================
    // COUNT SUBMISSIONS
    // =====================================================

    private long countSubmissions(
            List<Submission> submissions,
            SubmissionStatus status
    ) {

        return submissions.stream()
                .filter(submission ->
                        submission.getStatus() == status
                )
                .count();
    }

    // =====================================================
    // CALCULATE AVERAGE
    // =====================================================

    private double average(
            List<AiEvaluation> evaluations,
            Function<AiEvaluation, Double> valueExtractor
    ) {

        return evaluations.stream()
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    // =====================================================
    // INTERNAL SUBMISSION STATISTICS
    // =====================================================

    private record SubmissionStatistics(
            long submitted,
            long aiEvaluating,
            long underReview,
            long approved,
            long changesRequested
    ) {
    }

    // =====================================================
    // INTERNAL AI STATISTICS
    // =====================================================

    private record AverageStatistics(
            double completionPercentage,
            double qualityScore,
            double confidenceScore
    ) {
    }
}