package com.Workmanagement.integration;

import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.repository.NotificationPreferenceRepository;
import com.Workmanagement.notification.service.NotificationConnectionManager;
import com.Workmanagement.notification.service.NotificationPreferenceService;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone7Step6CommunicationAndHardeningIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationPreferenceService preferenceService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationConnectionManager connectionManager;

    @AfterEach
    void tearDown() {
        connectionManager.clearAll();
    }

    private User createTestUser(String name, String email, Role role) {
        return createUser(name, email, "Password123!", role);
    }

    private Project createTestProject(String name, User manager) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("Project description for " + name);
        project.setManager(manager);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStartDate(LocalDate.now());
        return projectRepository.save(project);
    }

    private Task createTestTask(String title, Project project, User assignee) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription("Task description for " + title);
        task.setProject(project);
        task.setAssignedTo(assignee);
        task.setPriority("MEDIUM");
        task.setStatus(TaskStatus.ASSIGNED);
        return taskRepository.save(task);
    }

    // =========================================================================
    // PART 1 & 2: TASK COMMENT CREATION & ACCESS CONTROL
    // =========================================================================

    @Test
    @DisplayName("Test 1: Authorized employee can create comment on assigned task")
    void testAuthorizedEmployeeCanComment() throws Exception {
        User manager = createTestUser("Manager One", "mgr1_m7s6@example.com", Role.MANAGER);
        User employee = createTestUser("Employee One", "emp1_m7s6@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Alpha", manager);
        Task task = createTestTask("Task 1", project, employee);

        String json = """
                {
                    "content": "I have started working on the feature implementation."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.taskId").value(task.getId()))
                .andExpect(jsonPath("$.authorId").value(employee.getId()))
                .andExpect(jsonPath("$.authorName").value("Employee One"))
                .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"))
                .andExpect(jsonPath("$.content").value("I have started working on the feature implementation."));

        // Verify manager received a GENERAL notification
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertFalse(notifications.isEmpty());
        assertTrue(notifications.stream().anyMatch(n -> n.getType() == NotificationType.GENERAL));
    }

    @Test
    @DisplayName("Test 2: Unauthorized employee cannot create comment on another employee's task")
    void testUnauthorizedEmployeeCannotComment() throws Exception {
        User manager = createTestUser("Manager Two", "mgr2_m7s6@example.com", Role.MANAGER);
        User employee1 = createTestUser("Employee One", "emp1_unauth@example.com", Role.EMPLOYEE);
        User employee2 = createTestUser("Employee Two", "emp2_unauth@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Beta", manager);
        Task task = createTestTask("Task for Emp 1", project, employee1);

        String json = """
                {
                    "content": "Attempting unauthorized comment on another employee's task."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 3: Authorized manager can comment on task within managed project")
    void testAuthorizedManagerCanComment() throws Exception {
        User manager = createTestUser("Project Lead", "lead_m7s6@example.com", Role.MANAGER);
        User employee = createTestUser("Dev One", "dev1_m7s6@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Gamma", manager);
        Task task = createTestTask("API Integration", project, employee);

        String json = """
                {
                    "content": "Please ensure you add unit tests for error cases."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(manager.getId()))
                .andExpect(jsonPath("$.authorRole").value("MANAGER"));

        // Verify assigned employee received a GENERAL notification
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertFalse(notifications.isEmpty());
        assertTrue(notifications.stream().anyMatch(n -> n.getType() == NotificationType.GENERAL));
    }

    @Test
    @DisplayName("Test 4: Unauthorized manager cannot comment on another manager's project task")
    void testUnauthorizedManagerCannotComment() throws Exception {
        User manager1 = createTestUser("Manager Alpha", "mgr_alpha@example.com", Role.MANAGER);
        User manager2 = createTestUser("Manager Beta", "mgr_beta@example.com", Role.MANAGER);
        User employee = createTestUser("Dev Two", "dev2_m7s6@example.com", Role.EMPLOYEE);
        Project project1 = createTestProject("Project 1", manager1);
        Task task = createTestTask("Task 1", project1, employee);

        String json = """
                {
                    "content": "Manager 2 trying to comment on Manager 1 project."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(manager2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 5: Admin can comment on any task")
    void testAdminCanCommentOnAnyTask() throws Exception {
        User admin = createTestUser("Admin User", "admin_m7s6@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Gamma", "mgr_gamma@example.com", Role.MANAGER);
        User employee = createTestUser("Dev Three", "dev3_m7s6@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Admin Review Project", manager);
        Task task = createTestTask("Task Admin", project, employee);

        String json = """
                {
                    "content": "Admin notice: Please review system compliance."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("ADMIN"));
    }

    // =========================================================================
    // PART 3: RETRIEVING COMMENTS
    // =========================================================================

    @Test
    @DisplayName("Test 6: Authorized users can retrieve task comments")
    void testAuthorizedUserCanGetTaskComments() throws Exception {
        User manager = createTestUser("Mgr Delta", "mgr_delta@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Delta", "emp_delta@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Delta", manager);
        Task task = createTestTask("Delta Task", project, employee);

        // Add 2 comments
        Comment c1 = Comment.builder().task(task).author(employee).content("First comment").build();
        Comment c2 = Comment.builder().task(task).author(manager).content("Second comment").build();
        commentRepository.save(c1);
        commentRepository.save(c2);

        // Employee can retrieve
        mockMvc.perform(get("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("First comment"))
                .andExpect(jsonPath("$[1].content").value("Second comment"));

        // Manager can retrieve
        mockMvc.perform(get("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Test 7: Unauthorized user cannot retrieve comments for inaccessible task")
    void testUnauthorizedUserCannotGetComments() throws Exception {
        User manager = createTestUser("Mgr Epsilon", "mgr_eps@example.com", Role.MANAGER);
        User employee1 = createTestUser("Emp Epsilon 1", "emp_eps1@example.com", Role.EMPLOYEE);
        User employee2 = createTestUser("Emp Epsilon 2", "emp_eps2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Epsilon", manager);
        Task task = createTestTask("Private Task", project, employee1);

        mockMvc.perform(get("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee2)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PART 4: EDITING & DELETION SECURITY
    // =========================================================================

    @Test
    @DisplayName("Test 8: Comment author can edit own comment")
    void testAuthorCanEditComment() throws Exception {
        User manager = createTestUser("Mgr Zeta", "mgr_zeta@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Zeta", "emp_zeta@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Zeta", manager);
        Task task = createTestTask("Zeta Task", project, employee);

        Comment comment = Comment.builder().task(task).author(employee).content("Initial note").build();
        Comment saved = commentRepository.save(comment);

        String updateJson = """
                {
                    "content": "Updated note with corrections."
                }
                """;

        mockMvc.perform(put("/api/comments/" + saved.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated note with corrections."));
    }

    @Test
    @DisplayName("Test 9: Non-author cannot edit another user's comment")
    void testNonAuthorCannotEditComment() throws Exception {
        User manager = createTestUser("Mgr Eta", "mgr_eta@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Eta", "emp_eta@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Eta", manager);
        Task task = createTestTask("Eta Task", project, employee);

        Comment comment = Comment.builder().task(task).author(employee).content("Employee comment").build();
        Comment saved = commentRepository.save(comment);

        String updateJson = """
                {
                    "content": "Manager trying to alter employee comment."
                }
                """;

        mockMvc.perform(put("/api/comments/" + saved.getId())
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 10: Non-author non-admin cannot delete another user's comment")
    void testNonAuthorCannotDeleteComment() throws Exception {
        User manager = createTestUser("Mgr Theta", "mgr_theta@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Theta", "emp_theta@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Theta", manager);
        Task task = createTestTask("Theta Task", project, employee);

        Comment comment = Comment.builder().task(task).author(employee).content("Employee note").build();
        Comment saved = commentRepository.save(comment);

        mockMvc.perform(delete("/api/comments/" + saved.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isForbidden());

        assertTrue(commentRepository.findById(saved.getId()).isPresent());
    }

    @Test
    @DisplayName("Test 11: Author or Admin can delete comment")
    void testAuthorOrAdminCanDeleteComment() throws Exception {
        User admin = createTestUser("Admin Theta", "admin_theta@example.com", Role.ADMIN);
        User manager = createTestUser("Mgr Iota", "mgr_iota@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Iota", "emp_iota@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Iota", manager);
        Task task = createTestTask("Iota Task", project, employee);

        // Test Author deletion
        Comment c1 = commentRepository.save(Comment.builder().task(task).author(employee).content("Author delete me").build());
        mockMvc.perform(delete("/api/comments/" + c1.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isNoContent());
        assertFalse(commentRepository.findById(c1.getId()).isPresent());

        // Test Admin deletion
        Comment c2 = commentRepository.save(Comment.builder().task(task).author(employee).content("Admin delete me").build());
        mockMvc.perform(delete("/api/comments/" + c2.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isNoContent());
        assertFalse(commentRepository.findById(c2.getId()).isPresent());
    }

    // =========================================================================
    // PART 5: VALIDATION & TAMPERING RESISTANCE
    // =========================================================================

    @Test
    @DisplayName("Test 12: Blank comment is rejected with 400 Bad Request")
    void testBlankCommentRejected() throws Exception {
        User manager = createTestUser("Mgr Kappa", "mgr_kappa@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Kappa", "emp_kappa@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Kappa", manager);
        Task task = createTestTask("Kappa Task", project, employee);

        String json = """
                {
                    "content": "   "
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 13: Oversized comment (> 3000 chars) is rejected with 400 Bad Request")
    void testOversizedCommentRejected() throws Exception {
        User manager = createTestUser("Mgr Lambda", "mgr_lambda@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Lambda", "emp_lambda@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Lambda", manager);
        Task task = createTestTask("Lambda Task", project, employee);

        String longContent = "A".repeat(3001);
        String json = "{\"content\":\"" + longContent + "\"}";

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 14: Request body authorId / role spoofing is ignored")
    void testRequestBodyTamperingIgnored() throws Exception {
        User manager = createTestUser("Mgr Mu", "mgr_mu@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Mu", "emp_mu@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Mu", manager);
        Task task = createTestTask("Mu Task", project, employee);

        String json = """
                {
                    "content": "Tampered request payload",
                    "authorId": 99999,
                    "authorRole": "ADMIN",
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(employee.getId()))
                .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"));
    }

    // =========================================================================
    // PART 6: PREFERENCE INTEGRATION & NOTIFICATION SUPPRESSION
    // =========================================================================

    @Test
    @DisplayName("Test 15: Comment notification respects notification preferences")
    void testCommentNotificationFollowsPreferences() throws Exception {
        User manager = createTestUser("Mgr Nu", "mgr_nu@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Nu", "emp_nu@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Nu", manager);
        Task task = createTestTask("Nu Task", project, employee);

        // Manager turns OFF GENERAL notifications
        preferenceService.updatePreference(manager.getId(), NotificationType.GENERAL, false);

        String json = """
                {
                    "content": "Employee says something here."
                }
                """;

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // Verify manager did NOT receive any notification
        List<Notification> managerNotifications = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertTrue(managerNotifications.isEmpty());

        // Now manager turns ON GENERAL notifications
        preferenceService.updatePreference(manager.getId(), NotificationType.GENERAL, true);

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // Now manager receives notification
        List<Notification> updatedNotifications = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertEquals(1, updatedNotifications.size());
    }

    // =========================================================================
    // PART 7: FULL END-TO-END WORKFLOW INTEGRATION
    // =========================================================================

    @Test
    @DisplayName("Test 16: Complete Task Lifecycle with Communication Hub and Notifications")
    void testCompleteLifecycleWithCommunicationHub() throws Exception {
        User manager = createTestUser("Full Workflow Mgr", "mgr_full_e2e@example.com", Role.MANAGER);
        User employee = createTestUser("Full Workflow Emp", "emp_full_e2e@example.com", Role.EMPLOYEE);
        Project project = createTestProject("E2E Project", manager);

        // 1. Manager assigns task -> TASK_ASSIGNED notification
        Task task = createTestTask("E2E Task", project, employee);
        notificationService.createNotification(
                employee.getId(),
                "Task Assigned",
                "You have been assigned task: " + task.getTitle(),
                NotificationType.TASK_ASSIGNED,
                "TASK",
                task.getId()
        );

        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId()).size());

        // 2. Employee comments on task
        String empComment = """
                { "content": "I am working on the initial architecture." }
                """;
        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(empComment))
                .andExpect(status().isCreated());

        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId()).size());

        // 3. Manager replies to comment
        String mgrComment = """
                { "content": "Looks good, let me know when ready for review." }
                """;
        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mgrComment))
                .andExpect(status().isCreated());

        assertEquals(2, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId()).size());

        // 4. Both users can view the comment thread
        mockMvc.perform(get("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 5. Employee submits work
        String submissionJson = """
                {
                    "report": "Completed the feature and tests.",
                    "githubUrl": "https://github.com/test/repo/pull/1",
                    "evidenceUrl": "https://preview.app/demo"
                }
                """;
        mockMvc.perform(post("/api/submissions?taskId=" + task.getId())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson))
                .andExpect(status().isOk());

        // 6. Verify total comments persisted
        assertEquals(2, commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).size());
    }
}
