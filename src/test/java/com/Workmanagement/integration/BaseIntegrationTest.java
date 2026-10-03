package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.ai.service.AiAgentService;
import com.Workmanagement.ai.service.AiPromptService;
import com.Workmanagement.ai.service.AiRiskService;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.auth.security.JwtService;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    protected MockMvc mockMvc;

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected TaskRequirementRepository requirementRepository;

    @Autowired
    protected SubmissionRepository submissionRepository;

    @Autowired
    protected AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected com.Workmanagement.notification.repository.NotificationPreferenceRepository preferenceRepository;

    @Autowired
    protected CommentRepository commentRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @MockitoBean
    protected AiPromptService aiPromptService;

    @MockitoBean
    protected AiRiskService aiRiskService;

    @MockitoBean
    protected AiAgentService aiAgentService;

    @BeforeEach
    void baseSetUp() {
        Filter springSecurityFilterChain = (Filter) webApplicationContext.getBean("springSecurityFilterChain");
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();
        cleanDatabase();
    }

    protected void cleanDatabase() {
        auditLogRepository.deleteAll();
        commentRepository.deleteAll();
        if (preferenceRepository != null) {
            preferenceRepository.deleteAll();
        }
        notificationRepository.deleteAll();
        aiEvaluationRepository.deleteAll();
        submissionRepository.deleteAll();
        requirementRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String name, String email, String rawPassword, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user);
    }

    protected Cookie createAuthCookie(User user) {
        String token = jwtService.generateToken(user.getEmail());
        Cookie cookie = new Cookie("jwt", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    protected Project createProject(String name, String description, User manager, ProjectStatus status) {
        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setManager(manager);
        project.setStatus(status != null ? status : ProjectStatus.PLANNED);
        project.setCreatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    protected Task createTask(String title, String description, Project project, User assignedTo, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setProject(project);
        task.setAssignedTo(assignedTo);
        task.setStatus(status != null ? status : TaskStatus.ASSIGNED);
        task.setPriority("HIGH");
        task.setEndDate(LocalDate.now().plusDays(7));
        return taskRepository.save(task);
    }

    protected TaskRequirement createRequirement(Task task, String description, Integer weight, Boolean mandatory) {
        TaskRequirement requirement = new TaskRequirement();
        requirement.setTask(task);
        requirement.setDescription(description);
        requirement.setWeight(weight != null ? weight : 10);
        requirement.setMandatory(mandatory != null ? mandatory : true);
        return requirementRepository.save(requirement);
    }

    protected Submission createSubmission(Task task, User submitter, String report, String githubUrl, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setTask(task);
        submission.setSubmittedBy(submitter);
        submission.setReport(report);
        submission.setGithubUrl(githubUrl);
        submission.setStatus(status != null ? status : SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
        return submissionRepository.save(submission);
    }

    protected AiEvaluation createAiEvaluation(Submission submission, Double completionPercentage, Double qualityScore, AiEvaluationStatus status) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setSubmission(submission);
        evaluation.setCompletionPercentage(completionPercentage);
        evaluation.setQualityScore(qualityScore);
        evaluation.setConfidenceScore(90.0);
        evaluation.setFeedback("Good work on the submission");
        evaluation.setStatus(status != null ? status : AiEvaluationStatus.COMPLETED);
        return aiEvaluationRepository.save(evaluation);
    }

    protected Comment createComment(Task task, User author, String content) {
        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    protected Notification createNotification(User recipient, String title, String message, NotificationType type) {
        return createNotification(recipient, title, message, type, null, null);
    }

    protected Notification createNotification(User recipient, String title, String message, NotificationType type, String relatedType, Long relatedId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type != null ? type : NotificationType.TASK_ASSIGNED);
        notification.setRead(false);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    protected AuditLog createAuditLog(User performedBy, AuditAction action, String entityType, Long entityId, String description) {
        AuditLog log = new AuditLog();
        log.setPerformedBy(performedBy);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setCreatedAt(LocalDateTime.now());
        return auditLogRepository.save(log);
    }
}
