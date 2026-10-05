package com.Workmanagement.security;

import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.ai.tools.ProjectTools;
import com.Workmanagement.ai.tools.SubmissionTools;
import com.Workmanagement.ai.tools.TaskTools;
import com.Workmanagement.ai.tools.UserTools;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.comment.model.CreateCommentRequest;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.comment.service.CommentService;
import com.Workmanagement.notification.entity.Notification;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityHardeningTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

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

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    private CurrentUserService currentUserService;
    private AuthorizationService authorizationService;

    private User admin;
    private User managerA;
    private User managerB;
    private User employee1;
    private User employee2;

    private Project projectA;
    private Project projectB;
    private Task taskA;
    private Task taskB;
    private Submission submissionA;
    private Submission submissionB;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(userRepository);
        authorizationService = new AuthorizationService();

        admin = new User(1L, "Admin User", "admin@test.com", "pass", Role.ADMIN);
        managerA = new User(2L, "Manager A", "managera@test.com", "pass", Role.MANAGER);
        managerB = new User(3L, "Manager B", "managerb@test.com", "pass", Role.MANAGER);
        employee1 = new User(4L, "Employee 1", "emp1@test.com", "pass", Role.EMPLOYEE);
        employee2 = new User(5L, "Employee 2", "emp2@test.com", "pass", Role.EMPLOYEE);

        projectA = new Project();
        projectA.setId(101L);
        projectA.setName("Project Alpha");
        projectA.setManager(managerA);

        projectB = new Project();
        projectB.setId(102L);
        projectB.setName("Project Beta");
        projectB.setManager(managerB);

        taskA = new Task();
        taskA.setId(201L);
        taskA.setTitle("Task A");
        taskA.setProject(projectA);
        taskA.setAssignedTo(employee1);
        taskA.setStatus(TaskStatus.ASSIGNED);

        taskB = new Task();
        taskB.setId(202L);
        taskB.setTitle("Task B");
        taskB.setProject(projectB);
        taskB.setAssignedTo(employee2);
        taskB.setStatus(TaskStatus.ASSIGNED);

        submissionA = new Submission();
        submissionA.setId(301L);
        submissionA.setTask(taskA);
        submissionA.setSubmittedBy(employee1);
        submissionA.setStatus(SubmissionStatus.UNDER_REVIEW);

        submissionB = new Submission();
        submissionB.setId(302L);
        submissionB.setTask(taskB);
        submissionB.setSubmittedBy(employee2);
        submissionB.setStatus(SubmissionStatus.UNDER_REVIEW);
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
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    // ==========================================
    // 1. EMPLOYEE SECURITY TESTS
    // ==========================================

    @Test
    @DisplayName("Employee can access own tasks via getMyTasks and cannot access another employee's tasks")
    void testEmployeeTaskIsolation() {
        authenticateAs(employee1);
        TaskService taskService = new TaskService(taskRepository, projectRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);

        when(taskRepository.findByAssignedTo(employee1)).thenReturn(List.of(taskA));
        List<Task> myTasks = taskService.getMyTasks();
        assertEquals(1, myTasks.size());
        assertEquals(taskA.getId(), myTasks.get(0).getId());

        // Employee 1 trying to access Employee 2's task list
        assertThrows(AccessDeniedException.class, () -> taskService.getTasksByEmployee(employee2.getId()));

        // Employee 1 trying to access Task B directly
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        assertThrows(AccessDeniedException.class, () -> taskService.getTaskById(taskB.getId()));
    }

    @Test
    @DisplayName("Employee cannot directly set task status to COMPLETED or APPROVED")
    void testEmployeeStatusChangeForbidden() {
        authenticateAs(employee1);
        TaskService taskService = new TaskService(taskRepository, projectRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));

        // Employee can set IN_PROGRESS
        when(taskRepository.save(any(Task.class))).thenReturn(taskA);
        assertDoesNotThrow(() -> taskService.updateTaskStatus(taskA.getId(), TaskStatus.IN_PROGRESS));

        // Employee cannot set COMPLETED directly
        assertThrows(AccessDeniedException.class, () -> taskService.updateTaskStatus(taskA.getId(), TaskStatus.COMPLETED));

        // Employee cannot set APPROVED directly
        assertThrows(AccessDeniedException.class, () -> taskService.updateTaskStatus(taskA.getId(), TaskStatus.APPROVED));
    }

    @Test
    @DisplayName("Employee submission creation binds to authenticated user and denies unassigned tasks")
    void testEmployeeSubmissionCreationSafety() {
        authenticateAs(employee1);
        SubmissionService submissionService = new SubmissionService(submissionRepository, taskRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService);

        // Submitting to Task A (assigned to Employee 1) -> Success
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        Submission newSub = new Submission();
        newSub.setReport("Implemented task deliverables");
        when(submissionRepository.save(any(Submission.class))).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setId(999L);
            return s;
        });

        Submission created = submissionService.createSubmission(taskA.getId(), 999L /* spoofed ID */, newSub);
        assertEquals(employee1.getId(), created.getSubmittedBy().getId(), "Submission must be bound to authenticated employee regardless of client param");

        // Submitting to Task B (assigned to Employee 2) -> 403 Forbidden
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        assertThrows(AccessDeniedException.class, () -> submissionService.createSubmission(taskB.getId(), employee1.getId(), newSub));
    }

    @Test
    @DisplayName("Employee cannot access another employee's submissions")
    void testEmployeeSubmissionIsolation() {
        authenticateAs(employee1);
        SubmissionService submissionService = new SubmissionService(submissionRepository, taskRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService);

        // Employee 1 viewing Employee 2 submissions -> 403
        assertThrows(AccessDeniedException.class, () -> submissionService.getSubmissionsByEmployee(employee2.getId()));

        // Employee 1 viewing Submission B directly -> 403
        when(submissionRepository.findById(submissionB.getId())).thenReturn(Optional.of(submissionB));
        assertThrows(AccessDeniedException.class, () -> submissionService.getSubmissionById(submissionB.getId()));
    }

    @Test
    @DisplayName("Employee cannot mutate another user's notifications")
    void testEmployeeNotificationIsolation() {
        authenticateAs(employee1);
        NotificationService notifService = new NotificationService(notificationRepository, userRepository, auditLogService);

        Notification notif2 = new Notification();
        notif2.setId(777L);
        notif2.setRecipient(employee2);
        notif2.setRead(false);

        when(notificationRepository.findById(777L)).thenReturn(Optional.of(notif2));
        assertThrows(AccessDeniedException.class, () -> notifService.markAsRead(777L));
    }

    @Test
    @DisplayName("Employee cannot comment on unassigned tasks")
    void testEmployeeCommentIsolation() {
        authenticateAs(employee1);
        CommentService commentService = new CommentService(commentRepository, taskRepository, userRepository, notificationService, auditLogService);

        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        CreateCommentRequest req = new CreateCommentRequest("Trying to comment on task B");

        assertThrows(AccessDeniedException.class, () -> commentService.createComment(taskB.getId(), req));
    }

    // ==========================================
    // 2. MANAGER ISOLATION TESTS
    // ==========================================

    @Test
    @DisplayName("Manager A cannot modify, delete, or create tasks in Manager B's projects")
    void testManagerProjectIsolation() {
        authenticateAs(managerA);
        ProjectService projectService = new ProjectService(projectRepository, userRepository, auditLogService, currentUserService, authorizationService, taskRepository, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);
        TaskService taskService = new TaskService(taskRepository, projectRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);

        when(projectRepository.findById(projectB.getId())).thenReturn(Optional.of(projectB));

        // Update Project B -> 403
        Project update = new Project();
        update.setName("Hacked Name");
        assertThrows(AccessDeniedException.class, () -> projectService.updateProject(projectB.getId(), update));

        // Delete Project B -> 403
        assertThrows(AccessDeniedException.class, () -> projectService.deleteProjectById(projectB.getId()));

        // Create task in Project B -> 403
        Task newTask = new Task();
        newTask.setTitle("Illegal Task");
        assertThrows(AccessDeniedException.class, () -> taskService.createTask(newTask, projectB.getId(), employee1.getId()));
    }

    @Test
    @DisplayName("Manager A cannot approve or request changes on submissions in Manager B's projects")
    void testManagerSubmissionReviewIsolation() {
        authenticateAs(managerA);
        SubmissionService submissionService = new SubmissionService(submissionRepository, taskRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService);

        when(submissionRepository.findById(submissionB.getId())).thenReturn(Optional.of(submissionB));

        assertThrows(AccessDeniedException.class, () -> submissionService.approveSubmission(submissionB.getId()));
        assertThrows(AccessDeniedException.class, () -> submissionService.requestChanges(submissionB.getId()));
    }

    @Test
    @DisplayName("Manager A cannot modify task requirements in Manager B's tasks")
    void testManagerRequirementIsolation() {
        authenticateAs(managerA);
        TaskRequirementService reqService = new TaskRequirementService(requirementRepository, taskRepository, auditLogService, currentUserService, authorizationService);

        TaskRequirement reqB = new TaskRequirement();
        reqB.setId(88L);
        reqB.setTask(taskB);

        when(requirementRepository.findById(88L)).thenReturn(Optional.of(reqB));

        assertThrows(AccessDeniedException.class, () -> reqService.updateRequirement(88L, reqB));
        assertThrows(AccessDeniedException.class, () -> reqService.deleteRequirement(88L));
    }

    // ==========================================
    // 3. AI AGENT ISOLATION TESTS
    // ==========================================

    @Test
    @DisplayName("AI Tools: Manager A can only query their own projects, tasks, and submissions")
    void testAiToolsManagerIsolation() {
        authenticateAs(managerA);

        // ProjectTools
        ProjectTools projectTools = new ProjectTools(projectRepository, currentUserService, authorizationService);
        when(projectRepository.findByManagerId(managerA.getId())).thenReturn(List.of(projectA));

        var projects = projectTools.getAllProjects();
        assertEquals(1, projects.size());
        assertEquals(projectA.getId(), projects.get(0).id());

        // Manager A trying to get Project B via tool
        when(projectRepository.findById(projectB.getId())).thenReturn(Optional.of(projectB));
        assertThrows(RuntimeException.class, () -> projectTools.getProject(projectB.getId()));

        // TaskTools
        TaskTools taskTools = new TaskTools(taskRepository, projectRepository, currentUserService, authorizationService);
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        assertThrows(RuntimeException.class, () -> taskTools.getTask(taskB.getId()));

        // SubmissionTools
        SubmissionTools submissionTools = new SubmissionTools(submissionRepository, taskRepository, currentUserService, authorizationService);
        when(submissionRepository.findById(submissionB.getId())).thenReturn(Optional.of(submissionB));
        assertThrows(RuntimeException.class, () -> submissionTools.getSubmission(submissionB.getId()));

        // UserTools
        UserTools userTools = new UserTools(userRepository, currentUserService);
        when(userRepository.findById(managerB.getId())).thenReturn(Optional.of(managerB));
        // Manager A cannot query Manager B details
        assertThrows(RuntimeException.class, () -> userTools.getUser(managerB.getId()));
    }

    // ==========================================
    // 4. ADMIN PRIVILEGES TESTS
    // ==========================================

    @Test
    @DisplayName("Admin has global access to all projects, tasks, and submissions")
    void testAdminGlobalAccess() {
        authenticateAs(admin);

        ProjectService projectService = new ProjectService(projectRepository, userRepository, auditLogService, currentUserService, authorizationService, taskRepository, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);
        TaskService taskService = new TaskService(taskRepository, projectRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService, submissionRepository, aiEvaluationRepository, commentRepository, requirementRepository);
        SubmissionService submissionService = new SubmissionService(submissionRepository, taskRepository, userRepository, notificationService, auditLogService, currentUserService, authorizationService);

        when(projectRepository.findById(projectB.getId())).thenReturn(Optional.of(projectB));
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        when(submissionRepository.findById(submissionB.getId())).thenReturn(Optional.of(submissionB));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submissionB);

        // Admin can access Project B, Task B, Submission B
        assertDoesNotThrow(() -> projectService.getProjectById(projectB.getId()));
        assertDoesNotThrow(() -> taskService.getTaskById(taskB.getId()));
        assertDoesNotThrow(() -> submissionService.getSubmissionById(submissionB.getId()));
        assertDoesNotThrow(() -> submissionService.approveSubmission(submissionB.getId()));
    }
}
