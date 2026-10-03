package com.Workmanagement.audit;

import com.Workmanagement.audit.controller.AuditLogController;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.model.AuditLogResponse;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TaskRequirementRepository taskRequirementRepository;

    @Mock
    private AiEvaluationRepository aiEvaluationRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private User adminUser;
    private User managerA;
    private User managerB;
    private User employeeA;
    private User employeeB;

    @BeforeEach
    void setUp() {
        adminUser = new User(1L, "Admin User", "admin@example.com", "secret", Role.ADMIN);
        managerA = new User(2L, "Manager A", "managera@example.com", "secret", Role.MANAGER);
        managerB = new User(3L, "Manager B", "managerb@example.com", "secret", Role.MANAGER);
        employeeA = new User(4L, "Employee A", "employeea@example.com", "secret", Role.EMPLOYEE);
        employeeB = new User(5L, "Employee B", "employeeb@example.com", "secret", Role.EMPLOYEE);
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
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Create log with authenticated user in SecurityContext")
    void testCreateLogWithAuth() {
        authenticateAs(managerA);

        AuditLog savedLog = AuditLog.builder()
                .id(100L)
                .performedBy(managerA)
                .action(AuditAction.PROJECT_CREATED)
                .entityType("PROJECT")
                .entityId(10L)
                .description("Created project Alpha")
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditLogResponse response = auditLogService.createLog(
                AuditAction.PROJECT_CREATED,
                "PROJECT",
                10L,
                "Created project Alpha"
        );

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(managerA.getId(), response.performedById());
        assertEquals(managerA.getName(), response.performedByName());
        assertEquals(managerA.getEmail(), response.performedByEmail());
        assertEquals("PROJECT_CREATED", response.action());
        assertEquals("PROJECT", response.entityType());
        assertEquals(10L, response.entityId());
    }

    @Test
    @DisplayName("Create log for system/internal operation without authenticated user")
    void testCreateLogWithoutAuth() {
        SecurityContextHolder.clearContext();

        AuditLog savedLog = AuditLog.builder()
                .id(101L)
                .performedBy(null)
                .action(AuditAction.NOTIFICATION_CREATED)
                .entityType("NOTIFICATION")
                .entityId(20L)
                .description("System generated notification")
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditLogResponse response = auditLogService.createLog(
                AuditAction.NOTIFICATION_CREATED,
                "NOTIFICATION",
                20L,
                "System generated notification"
        );

        assertNotNull(response);
        assertNull(response.performedById());
        assertNull(response.performedByName());
        assertEquals("NOTIFICATION_CREATED", response.action());
    }

    @Test
    @DisplayName("Get My Logs returns only logs performed by the authenticated user")
    void testGetMyLogs() {
        authenticateAs(employeeA);

        AuditLog log1 = AuditLog.builder()
                .id(1L)
                .performedBy(employeeA)
                .action(AuditAction.SUBMISSION_CREATED)
                .entityType("SUBMISSION")
                .entityId(50L)
                .description("Submission created")
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogRepository.findByPerformedByIdOrderByCreatedAtDesc(employeeA.getId()))
                .thenReturn(List.of(log1));

        List<AuditLogResponse> logs = auditLogService.getMyLogs();
        assertEquals(1, logs.size());
        assertEquals(employeeA.getId(), logs.get(0).performedById());
    }

    @Test
    @DisplayName("Admin can view any entity logs")
    void testAdminEntityAccess() {
        authenticateAs(adminUser);

        AuditLog log = AuditLog.builder()
                .id(1L)
                .performedBy(employeeA)
                .action(AuditAction.TASK_CREATED)
                .entityType("TASK")
                .entityId(10L)
                .description("Task created")
                .createdAt(LocalDateTime.now())
                .build();

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("TASK", 10L))
                .thenReturn(List.of(log));

        List<AuditLogResponse> result = auditLogService.getEntityLogs("TASK", 10L);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Employee A cannot view logs for Employee B's assigned task")
    void testEmployeeIsolationOnTask() {
        authenticateAs(employeeA);

        Task taskB = new Task();
        taskB.setId(10L);
        taskB.setAssignedTo(employeeB);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskB));

        assertThrows(AccessDeniedException.class, () ->
                auditLogService.getEntityLogs("TASK", 10L));
    }

    @Test
    @DisplayName("Employee A can view logs for their own assigned task")
    void testEmployeeAccessOwnTask() {
        authenticateAs(employeeA);

        Task taskA = new Task();
        taskA.setId(10L);
        taskA.setAssignedTo(employeeA);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskA));
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("TASK", 10L))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getEntityLogs("TASK", 10L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Manager A cannot view logs for Manager B's project task")
    void testManagerIsolationOnTask() {
        authenticateAs(managerA);

        Project projectB = new Project();
        projectB.setId(100L);
        projectB.setManager(managerB);

        Task taskInProjectB = new Task();
        taskInProjectB.setId(10L);
        taskInProjectB.setProject(projectB);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskInProjectB));

        assertThrows(AccessDeniedException.class, () ->
                auditLogService.getEntityLogs("TASK", 10L));
    }

    @Test
    @DisplayName("Manager A can view logs for tasks in their managed project")
    void testManagerAccessOwnProjectTask() {
        authenticateAs(managerA);

        Project projectA = new Project();
        projectA.setId(100L);
        projectA.setManager(managerA);

        Task taskInProjectA = new Task();
        taskInProjectA.setId(10L);
        taskInProjectA.setProject(projectA);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskInProjectA));
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("TASK", 10L))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getEntityLogs("TASK", 10L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Employee cannot view other employee submission logs")
    void testEmployeeSubmissionIsolation() {
        authenticateAs(employeeA);

        Submission submissionB = new Submission();
        submissionB.setId(20L);
        submissionB.setSubmittedBy(employeeB);

        when(submissionRepository.findById(20L)).thenReturn(Optional.of(submissionB));

        assertThrows(AccessDeniedException.class, () ->
                auditLogService.getEntityLogs("SUBMISSION", 20L));
    }

    @Test
    @DisplayName("User can view logs for comments they authored")
    void testCommentAuthorAccess() {
        authenticateAs(employeeA);

        Comment comment = new Comment();
        comment.setId(30L);
        comment.setAuthor(employeeA);

        when(commentRepository.findById(30L)).thenReturn(Optional.of(comment));
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("COMMENT", 30L))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getEntityLogs("COMMENT", 30L);
        assertNotNull(result);
    }
}
