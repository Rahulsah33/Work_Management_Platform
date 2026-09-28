package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.common.dto.ProjectToolResult;
import com.Workmanagement.common.dto.RequirementToolResult;
import com.Workmanagement.common.dto.SubmissionToolResult;
import com.Workmanagement.common.dto.TaskToolResult;
import com.Workmanagement.common.dto.UserToolResult;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Maps JPA entities to flat AI/DTO results.
 * <p>
 * Central place that guarantees:
 * - no Hibernate lazy proxies reach Jackson (no hibernateLazyInitializer)
 * - no circular entity graphs
 * - User passwords are never exposed
 */
@Service
public class ToolMapper {

    private final AiEvaluationRepository aiEvaluationRepository;

    public ToolMapper(AiEvaluationRepository aiEvaluationRepository) {
        this.aiEvaluationRepository = aiEvaluationRepository;
    }

    public UserToolResult toUserToolResult(User user) {
        if (user == null) {
            return null;
        }
        return UserToolResult.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .build();
    }

    public RequirementToolResult toRequirementToolResult(TaskRequirement r) {
        if (r == null) {
            return null;
        }
        return RequirementToolResult.builder()
                .requirementId(r.getId())
                .description(r.getDescription())
                .weight(r.getWeight())
                .mandatory(r.getMandatory())
                .build();
    }

    public TaskToolResult toTaskToolResult(Task task) {
        if (task == null) {
            return null;
        }

        Project project = task.getProject();
        User assignee = task.getAssignedTo();

        List<RequirementToolResult> requirements = task.getRequirements() == null
                ? List.of()
                : task.getRequirements().stream()
                        .map(this::toRequirementToolResult)
                        .toList();

        return TaskToolResult.builder()
                .taskId(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority())
                .deadline(task.getEndDate())
                .projectId(project != null ? project.getId() : null)
                .projectName(project != null ? project.getName() : null)
                .assignedEmployeeId(assignee != null ? assignee.getId() : null)
                .assignedEmployeeName(assignee != null ? assignee.getName() : null)
                .assignedEmployeeEmail(assignee != null ? assignee.getEmail() : null)
                .requirements(requirements)
                .build();
    }

    public SubmissionToolResult toSubmissionToolResult(Submission submission) {
        if (submission == null) {
            return null;
        }

        Task task = submission.getTask();
        User employee = submission.getSubmittedBy();

        Double completion = null;
        Double quality = null;
        String feedback = null;

        AiEvaluation evaluation =
                aiEvaluationRepository.findBySubmissionId(submission.getId())
                        .orElse(null);

        if (evaluation != null) {
            completion = evaluation.getCompletionPercentage();
            quality = evaluation.getQualityScore();
            feedback = evaluation.getFeedback();
        }

        return SubmissionToolResult.builder()
                .submissionId(submission.getId())
                .report(submission.getReport())
                .githubUrl(submission.getGithubUrl())
                .evidenceUrl(submission.getEvidenceUrl())
                .status(submission.getStatus() != null ? submission.getStatus().name() : null)
                .submittedAt(submission.getSubmittedAt())
                .taskId(task != null ? task.getId() : null)
                .taskTitle(task != null ? task.getTitle() : null)
                .submittedByEmployeeId(employee != null ? employee.getId() : null)
                .submittedByName(employee != null ? employee.getName() : null)
                .previousSubmissionId(submission.getPreviousSubmission() != null
                        ? submission.getPreviousSubmission().getId()
                        : null)
                .aiCompletionPercentage(completion)
                .aiQualityScore(quality)
                .aiFeedback(feedback)
                .build();
    }

    public ProjectToolResult toProjectToolResult(Project project) {
        if (project == null) {
            return null;
        }

        User manager = project.getManager();

        return ProjectToolResult.builder()
                .projectId(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus() != null ? project.getStatus().name() : null)
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .managerId(manager != null ? manager.getId() : null)
                .managerName(manager != null ? manager.getName() : null)
                .managerEmail(manager != null ? manager.getEmail() : null)
                .build();
    }
}
