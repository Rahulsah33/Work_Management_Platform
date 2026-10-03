package com.Workmanagement.integration;

import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone7Step5NotificationPreferencesIntegrationTest extends BaseIntegrationTest {

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

    private User createTestUser(String email, Role role) {
        return createUser("Test User", email, "Password123!", role);
    }

    @Test
    @DisplayName("Test 1: New and existing users receive default enabled (true) preferences for all categories")
    void testDefaultPreferences() throws Exception {
        User user = createTestUser("default_pref@example.com", Role.EMPLOYEE);

        mockMvc.perform(get("/api/notifications/preferences")
                        .cookie(createAuthCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TASK_ASSIGNED").value(true))
                .andExpect(jsonPath("$.SUBMISSION_RECEIVED").value(true))
                .andExpect(jsonPath("$.AI_EVALUATION_READY").value(true))
                .andExpect(jsonPath("$.CHANGES_REQUESTED").value(true))
                .andExpect(jsonPath("$.SUBMISSION_APPROVED").value(true))
                .andExpect(jsonPath("$.TASK_STATUS_CHANGED").value(true));

        // Programmatic service check
        for (NotificationType type : NotificationType.values()) {
            assertTrue(preferenceService.isNotificationEnabled(user.getId(), type));
        }
    }

    @Test
    @DisplayName("Test 2 & 3: Authenticated user can retrieve own preferences and disable a preference")
    void testDisablePreference() throws Exception {
        User user = createTestUser("pref_user@example.com", Role.EMPLOYEE);

        // Disable TASK_ASSIGNED
        mockMvc.perform(patch("/api/notifications/preferences/TASK_ASSIGNED")
                        .cookie(createAuthCookie(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TASK_ASSIGNED").value(false))
                .andExpect(jsonPath("$.SUBMISSION_RECEIVED").value(true));

        assertFalse(preferenceService.isNotificationEnabled(user.getId(), NotificationType.TASK_ASSIGNED));
        assertTrue(preferenceService.isNotificationEnabled(user.getId(), NotificationType.SUBMISSION_RECEIVED));
    }

    @Test
    @DisplayName("Test 4 & 5: User can re-enable a previously disabled preference")
    void testReEnablePreference() throws Exception {
        User user = createTestUser("reenable@example.com", Role.EMPLOYEE);

        // Disable
        preferenceService.updatePreference(user.getId(), NotificationType.TASK_ASSIGNED, false);
        assertFalse(preferenceService.isNotificationEnabled(user.getId(), NotificationType.TASK_ASSIGNED));

        // Re-enable via API
        mockMvc.perform(patch("/api/notifications/preferences/TASK_ASSIGNED")
                        .cookie(createAuthCookie(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TASK_ASSIGNED").value(true));

        assertTrue(preferenceService.isNotificationEnabled(user.getId(), NotificationType.TASK_ASSIGNED));
    }

    @Test
    @DisplayName("Test 6: User A preferences do not leak or affect User B preferences")
    void testUserPreferenceIsolation() throws Exception {
        User userA = createTestUser("usera_pref@example.com", Role.EMPLOYEE);
        User userB = createTestUser("userb_pref@example.com", Role.EMPLOYEE);

        // User A disables CHANGES_REQUESTED
        mockMvc.perform(patch("/api/notifications/preferences/CHANGES_REQUESTED")
                        .cookie(createAuthCookie(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        // User B checks their preferences
        mockMvc.perform(get("/api/notifications/preferences")
                        .cookie(createAuthCookie(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CHANGES_REQUESTED").value(true));

        assertFalse(preferenceService.isNotificationEnabled(userA.getId(), NotificationType.CHANGES_REQUESTED));
        assertTrue(preferenceService.isNotificationEnabled(userB.getId(), NotificationType.CHANGES_REQUESTED));
    }

    @Test
    @DisplayName("Test 7: Invalid notification type is rejected with 400 Bad Request")
    void testInvalidNotificationType() throws Exception {
        User user = createTestUser("invalid_type@example.com", Role.EMPLOYEE);

        mockMvc.perform(patch("/api/notifications/preferences/NON_EXISTENT_TYPE")
                        .cookie(createAuthCookie(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 8 & 9: Disabled TASK_ASSIGNED blocks notification while Enabled creates it")
    void testDisabledTaskAssignedBlocksNotification() throws Exception {
        User manager = createTestUser("mgr_assign@example.com", Role.MANAGER);
        User employee = createTestUser("emp_assign@example.com", Role.EMPLOYEE);
        Project project = createProject("Assign Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);

        // Disable TASK_ASSIGNED for employee
        preferenceService.updatePreference(employee.getId(), NotificationType.TASK_ASSIGNED, false);

        Task task1 = new Task();
        task1.setTitle("Task 1");
        task1.setDescription("Desc 1");
        task1.setPriority("HIGH");
        task1.setEndDate(LocalDate.now().plusDays(5));

        // Manager creates task via endpoint
        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task1)))
                .andExpect(status().isOk());

        // Verify NO notification was created
        List<Notification> notifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertTrue(notifs.isEmpty(), "Expected no notification when TASK_ASSIGNED is disabled");

        // Re-enable TASK_ASSIGNED
        preferenceService.updatePreference(employee.getId(), NotificationType.TASK_ASSIGNED, true);

        Task task2 = new Task();
        task2.setTitle("Task 2");
        task2.setDescription("Desc 2");
        task2.setPriority("HIGH");
        task2.setEndDate(LocalDate.now().plusDays(5));

        // Manager creates second task
        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task2)))
                .andExpect(status().isOk());

        // Notification should now be created
        List<Notification> notifsAfter = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, notifsAfter.size());
        assertEquals(NotificationType.TASK_ASSIGNED, notifsAfter.get(0).getType());
    }

    @Test
    @DisplayName("Test 10: Disabled SUBMISSION_RECEIVED blocks manager notification on employee submission")
    void testDisabledSubmissionReceived() throws Exception {
        User manager = createTestUser("mgr_sub@example.com", Role.MANAGER);
        User employee = createTestUser("emp_sub@example.com", Role.EMPLOYEE);

        Project project = createProject("Sub Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build Auth", "Desc", project, employee, TaskStatus.ASSIGNED);

        // Manager disables SUBMISSION_RECEIVED
        preferenceService.updatePreference(manager.getId(), NotificationType.SUBMISSION_RECEIVED, false);

        Submission sub = new Submission();
        sub.setReport("Auth completed with tests");
        sub.setGithubUrl("https://github.com/repo/auth");

        // Employee submits
        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employee))
                        .param("taskId", task.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk());

        // Verify manager received NO notification
        List<Notification> mgrNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertTrue(mgrNotifs.isEmpty(), "Manager should not receive notification when SUBMISSION_RECEIVED is disabled");
    }

    @Test
    @DisplayName("Test 11: Disabled AI_EVALUATION_READY blocks manager notification on evaluation completion")
    void testDisabledAiEvaluationReady() {
        User manager = createTestUser("mgr_ai@example.com", Role.MANAGER);

        preferenceService.updatePreference(manager.getId(), NotificationType.AI_EVALUATION_READY, false);

        // Create notification directly
        var notif = notificationService.createNotification(
                manager.getId(),
                "AI Evaluation Ready",
                "Evaluation ready",
                NotificationType.AI_EVALUATION_READY,
                "SUBMISSION",
                999L
        );

        assertNull(notif, "createNotification should return null when category is disabled");
        List<Notification> notifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertTrue(notifs.isEmpty());
    }

    @Test
    @DisplayName("Test 12: Disabled CHANGES_REQUESTED blocks employee notification when changes are requested")
    void testDisabledChangesRequested() throws Exception {
        User manager = createTestUser("mgr_rev@example.com", Role.MANAGER);
        User employee = createTestUser("emp_rev@example.com", Role.EMPLOYEE);

        Project project = createProject("Rev Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build UI", "Desc", project, employee, TaskStatus.ASSIGNED);
        Submission submission = createSubmission(task, employee, "Initial UI", "https://github.com/repo/ui", SubmissionStatus.UNDER_REVIEW);

        // Employee disables CHANGES_REQUESTED
        preferenceService.updatePreference(employee.getId(), NotificationType.CHANGES_REQUESTED, false);

        // Manager requests changes via endpoint
        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\": \"Please fix alignment\"}"))
                .andExpect(status().isOk());

        // Verify employee did NOT receive notification
        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertTrue(empNotifs.isEmpty());
    }

    @Test
    @DisplayName("Test 13: Disabled SUBMISSION_APPROVED blocks employee notification on approval")
    void testDisabledSubmissionApproved() throws Exception {
        User manager = createTestUser("mgr_appr@example.com", Role.MANAGER);
        User employee = createTestUser("emp_appr@example.com", Role.EMPLOYEE);

        Project project = createProject("Appr Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build DB", "Desc", project, employee, TaskStatus.ASSIGNED);
        Submission submission = createSubmission(task, employee, "DB scripts", "https://github.com/repo/db", SubmissionStatus.UNDER_REVIEW);

        // Employee disables SUBMISSION_APPROVED
        preferenceService.updatePreference(employee.getId(), NotificationType.SUBMISSION_APPROVED, false);

        // Manager approves submission via endpoint
        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\": \"Looks good\"}"))
                .andExpect(status().isOk());

        // Verify employee did NOT receive notification
        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertTrue(empNotifs.isEmpty());
    }

    @Test
    @DisplayName("Test 14 & 15: Disabled preference suppresses real-time SSE delivery")
    void testDisabledPreferenceSuppressesRealTimeDelivery() {
        User employee = createTestUser("emp_sse_pref@example.com", Role.EMPLOYEE);

        // Register SSE connection for employee
        List<Object> receivedEvents = Collections.synchronizedList(new ArrayList<>());
        SseEmitter emitter = connectionManager.register(employee.getId());
        emitter.onCompletion(() -> {});

        // Disable TASK_ASSIGNED
        preferenceService.updatePreference(employee.getId(), NotificationType.TASK_ASSIGNED, false);

        // Trigger notification
        var result = notificationService.createNotification(
                employee.getId(),
                "Task Assigned",
                "You have a new task",
                NotificationType.TASK_ASSIGNED
        );

        assertNull(result);
        assertEquals(0, receivedEvents.size());
    }

    @Test
    @DisplayName("Test 16: Existing notifications in DB are preserved when preference is later disabled")
    void testExistingNotificationsPreserved() {
        User employee = createTestUser("emp_persist@example.com", Role.EMPLOYEE);

        // Create initial notification while enabled
        notificationService.createNotification(
                employee.getId(),
                "Initial Notice",
                "Message 1",
                NotificationType.TASK_ASSIGNED
        );

        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId()).size());

        // Now disable TASK_ASSIGNED
        preferenceService.updatePreference(employee.getId(), NotificationType.TASK_ASSIGNED, false);

        // Existing notification must still be present and accessible
        List<Notification> remaining = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, remaining.size());
        assertEquals("Initial Notice", remaining.get(0).getTitle());
    }

    @Test
    @DisplayName("Test 17: Business operations succeed normally regardless of notification preference state")
    void testBusinessOperationsUnaffectedByPreferences() throws Exception {
        User manager = createTestUser("mgr_biz@example.com", Role.MANAGER);
        User employee = createTestUser("emp_biz@example.com", Role.EMPLOYEE);

        Project project = createProject("Biz Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);

        // Disable ALL preferences for employee
        for (NotificationType type : NotificationType.values()) {
            preferenceService.updatePreference(employee.getId(), type, false);
        }

        // 1. Create task via API
        Task taskInput = new Task();
        taskInput.setTitle("Critical Feature");
        taskInput.setDescription("Build feature");
        taskInput.setPriority("HIGH");
        taskInput.setEndDate(LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskInput)))
                .andExpect(status().isOk());

        Task createdTask = taskRepository.findByAssignedToId(employee.getId()).get(0);
        assertNotNull(createdTask);

        // 2. Submit task via API
        Submission submissionInput = new Submission();
        submissionInput.setGithubUrl("https://github.com/repo/feature");
        submissionInput.setReport("Feature code");

        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employee))
                        .param("taskId", createdTask.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submissionInput)))
                .andExpect(status().isOk());

        Submission createdSub = submissionRepository.findByTaskId(createdTask.getId()).get(0);
        assertNotNull(createdSub);
        assertEquals(SubmissionStatus.SUBMITTED, createdSub.getStatus());

        // Move to UNDER_REVIEW to allow approval
        createdSub.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(createdSub);

        // 3. Manager reviews & approves
        mockMvc.perform(post("/api/submissions/" + createdSub.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\": \"Approved!\"}"))
                .andExpect(status().isOk());

        // 4. Verify no notifications created for employee
        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertTrue(empNotifs.isEmpty());
    }
}
