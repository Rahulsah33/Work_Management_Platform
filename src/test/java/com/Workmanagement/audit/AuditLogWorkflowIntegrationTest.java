package com.Workmanagement.audit;

import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.model.AuditLogResponse;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.model.NotificationResponse;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.project.service.ProjectService;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.submission.service.SubmissionService;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.task.service.TaskRequirementService;
import com.Workmanagement.task.service.TaskService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogWorkflowIntegrationTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskRequirementRepository requirementRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AiEvaluationRepository aiEvaluationRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CommentRepository commentRepository;

    private User manager;
    private User employee;

    private CurrentUserService currentUserService;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        manager = new User(1L, "Manager Bob", "manager@test.com", "pass", Role.MANAGER);
        employee = new User(2L, "Employee Alice", "alice@test.com", "pass", Role.EMPLOYEE);
        currentUserService = new CurrentUserService(userRepository);
        authorizationService = new AuthorizationService();
    }

    private void authenticateAs(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Project workflow logs PROJECT_CREATED, PROJECT_UPDATED, PROJECT_DELETED")
    void testProjectWorkflowAudit() {
        authenticateAs(manager);
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        ProjectService projectService = new ProjectService(projectRepository, userRepository, auditLogService, currentUserService, authorizationService, taskRepository, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);

        Project project = new Project();
        project.setId(10L);
        project.setName("Beta Project");
        project.setManager(manager);

        when(projectRepository.save(any(Project.class))).thenReturn(project);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        // Create
        projectService.createProject(project, "manager@test.com");
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.PROJECT_CREATED), eq("PROJECT"), eq(10L), contains("Beta Project"));

        // Update
        Project updateData = new Project();
        updateData.setName("Beta Project Updated");
        projectService.updateProject(10L, updateData);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.PROJECT_UPDATED), eq("PROJECT"), eq(10L), contains("Beta Project Updated"));

        // Delete
        projectService.deleteProjectById(10L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.PROJECT_DELETED), eq("PROJECT"), eq(10L), contains("Beta Project"));
    }

    @Test
    @DisplayName("Task workflow logs TASK_CREATED, TASK_UPDATED, TASK_STATUS_CHANGED, TASK_DELETED")
    void testTaskWorkflowAudit() {
        authenticateAs(manager);
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        NotificationService notificationService = mock(NotificationService.class);
        TaskService taskService = new TaskService(taskRepository, projectRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);

        Project project = new Project();
        project.setId(10L);
        project.setManager(manager);

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        Task task = new Task();
        task.setId(100L);
        task.setTitle("Design Database");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setProject(project);
        task.setAssignedTo(employee);

        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        // Create
        taskService.createTask(task, 10L, 2L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.TASK_CREATED), eq("TASK"), eq(100L), contains("Design Database"));

        // Update
        Task updatedData = new Task();
        updatedData.setTitle("Design Database V2");
        taskService.updateTask(100L, updatedData);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.TASK_UPDATED), eq("TASK"), eq(100L), contains("Design Database"));

        // Status Change
        taskService.updateTaskStatus(100L, TaskStatus.IN_PROGRESS);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.TASK_STATUS_CHANGED), eq("TASK"), eq(100L), contains("IN_PROGRESS"));

        // Delete
        taskService.deleteTask(100L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.TASK_DELETED), eq("TASK"), eq(100L), contains("Design Database"));
    }

    @Test
    @DisplayName("Submission workflow logs SUBMISSION_CREATED, SUBMISSION_STATUS_CHANGED, SUBMISSION_APPROVED, SUBMISSION_CHANGES_REQUESTED, SUBMISSION_RESUBMITTED")
    void testSubmissionWorkflowAudit() {
        authenticateAs(employee);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(employee));

        NotificationService notificationService = mock(NotificationService.class);
        SubmissionService submissionService = new SubmissionService(submissionRepository, taskRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService);

        Project project = new Project();
        project.setId(10L);
        project.setManager(manager);

        Task task = new Task();
        task.setId(100L);
        task.setTitle("Implement Audit");
        task.setAssignedTo(employee);
        task.setProject(project);

        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        Submission sub = new Submission();
        sub.setId(500L);
        sub.setTask(task);
        sub.setSubmittedBy(employee);
        sub.setReport("Implemented audit log trail");
        sub.setStatus(SubmissionStatus.UNDER_REVIEW);

        when(submissionRepository.save(any(Submission.class))).thenReturn(sub);
        when(submissionRepository.findById(500L)).thenReturn(Optional.of(sub));

        // Create
        submissionService.createSubmission(100L, 2L, sub);
        verify(auditLogService).createLog(eq(employee), eq(AuditAction.SUBMISSION_CREATED), eq("SUBMISSION"), eq(500L), contains("Implement Audit"));

        // Now manager review actions
        authenticateAs(manager);
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        // Status change
        submissionService.updateStatus(500L, SubmissionStatus.UNDER_REVIEW);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.SUBMISSION_STATUS_CHANGED), eq("SUBMISSION"), eq(500L), contains("UNDER_REVIEW"));

        // Approve
        submissionService.approveSubmission(500L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.SUBMISSION_APPROVED), eq("SUBMISSION"), eq(500L), contains("Implement Audit"));

        // Changes requested
        sub.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionService.requestChanges(500L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.SUBMISSION_CHANGES_REQUESTED), eq("SUBMISSION"), eq(500L), contains("Implement Audit"));

        // Resubmit by employee
        authenticateAs(employee);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(employee));
        sub.setStatus(SubmissionStatus.CHANGES_REQUESTED);
        Submission newSub = new Submission();
        newSub.setId(501L);
        newSub.setTask(task);
        newSub.setSubmittedBy(employee);
        newSub.setReport("Revised audit implementation");
        when(submissionRepository.save(any(Submission.class))).thenReturn(newSub);

        submissionService.resubmit(500L, newSub);
        verify(auditLogService).createLog(eq(employee), eq(AuditAction.SUBMISSION_RESUBMITTED), eq("SUBMISSION"), eq(501L), contains("Implement Audit"));
    }

    @Test
    @DisplayName("Task Requirement workflow logs REQUIREMENT_CREATED, REQUIREMENT_UPDATED, REQUIREMENT_DELETED")
    void testRequirementWorkflowAudit() {
        authenticateAs(manager);
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        TaskRequirementService reqService = new TaskRequirementService(requirementRepository, taskRepository, auditLogService, currentUserService, authorizationService);

        Project project = new Project();
        project.setId(10L);
        project.setManager(manager);

        Task task = new Task();
        task.setId(100L);
        task.setProject(project);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        TaskRequirement req = new TaskRequirement();
        req.setId(70L);
        req.setTask(task);
        req.setDescription("Requirement 1");
        req.setWeight(10);
        req.setMandatory(true);

        when(requirementRepository.save(any(TaskRequirement.class))).thenReturn(req);
        when(requirementRepository.findById(70L)).thenReturn(Optional.of(req));

        // Create
        reqService.createRequirement(100L, req);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.REQUIREMENT_CREATED), eq("TASK_REQUIREMENT"), eq(70L), contains("Requirement 1"));

        // Update
        TaskRequirement updatedReq = new TaskRequirement();
        updatedReq.setDescription("Requirement 1 Updated");
        reqService.updateRequirement(70L, updatedReq);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.REQUIREMENT_UPDATED), eq("TASK_REQUIREMENT"), eq(70L), contains("70"));

        // Delete
        reqService.deleteRequirement(70L);
        verify(auditLogService).createLog(eq(manager), eq(AuditAction.REQUIREMENT_DELETED), eq("TASK_REQUIREMENT"), eq(70L), contains("70"));
    }

    @Test
    @DisplayName("Notification creation logs NOTIFICATION_CREATED without recursion")
    void testNotificationAudit() {
        NotificationService notificationService = new NotificationService(notificationRepository, userRepository, auditLogService);

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        Notification notification = new Notification();
        notification.setId(88L);
        notification.setTitle("Task Notification");
        notification.setMessage("Work assigned");
        notification.setType(NotificationType.TASK_ASSIGNED);

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationResponse response = notificationService.createNotification(2L, "Task Notification", "Work assigned", NotificationType.TASK_ASSIGNED);

        assertNotNull(response);
        verify(auditLogService).createLog(eq(AuditAction.NOTIFICATION_CREATED), eq("NOTIFICATION"), eq(88L), contains("Task Notification"));
    }

    @Test
    @DisplayName("Serialization Safety: AuditLogResponse does not leak entities or passwords")
    void testSerializationSafety() {
        AuditLogResponse response = new AuditLogResponse(
                1L,
                manager.getId(),
                manager.getName(),
                manager.getEmail(),
                AuditAction.TASK_CREATED.name(),
                "TASK",
                50L,
                "Created task description",
                LocalDateTime.now()
        );

        assertEquals(1L, response.id());
        assertEquals("Manager Bob", response.performedByName());
        assertEquals("manager@test.com", response.performedByEmail());
        assertEquals("TASK_CREATED", response.action());
        assertEquals("TASK", response.entityType());
        assertEquals(50L, response.entityId());
    }
}
