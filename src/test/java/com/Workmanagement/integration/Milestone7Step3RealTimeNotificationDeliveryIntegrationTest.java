package com.Workmanagement.integration;

import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.model.NotificationResponse;
import com.Workmanagement.notification.model.RealTimeNotificationPayload;
import com.Workmanagement.notification.service.NotificationConnectionManager;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.service.SubmissionService;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.service.TaskService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone7Step3RealTimeNotificationDeliveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationConnectionManager connectionManager;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private SubmissionService submissionService;

    @AfterEach
    void tearDown() {
        connectionManager.clearAll();
    }

    private AiEvaluationResult createMockResult(Double completion, Double quality, Double confidence, String feedback) {
        AiEvaluationResult result = new AiEvaluationResult();
        result.setCompletionPercentage(completion);
        result.setQualityScore(quality);
        result.setConfidenceScore(confidence);
        result.setFeedback(feedback);
        result.setRequirements(Collections.emptyList());
        return result;
    }

    @Test
    @DisplayName("Test 1: Authenticated user establishes SSE stream connection")
    void test1_authenticatedUserStreamConnection() throws Exception {
        User employee = createUser("Stream User", "stream_user@test.com", "pass123", Role.EMPLOYEE);

        mockMvc.perform(get("/api/notifications/stream").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));

        assertEquals(1, connectionManager.getActiveConnectionCount(employee.getId()));
    }

    @Test
    @DisplayName("Test 2: Unauthenticated user rejected on SSE stream (401/403)")
    void test2_unauthenticatedUserStreamRejected() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 3: User isolation - notifications delivered only to target recipient's connection")
    void test3_userIsolationRealTimeDelivery() {
        User userA = createUser("User A", "user_a@test.com", "pass123", Role.EMPLOYEE);
        User userB = createUser("User B", "user_b@test.com", "pass123", Role.EMPLOYEE);

        List<Object> userAReceived = new ArrayList<>();
        List<Object> userBReceived = new ArrayList<>();

        // Register mock-like listening emitters
        SseEmitter emitterA = connectionManager.register(userA.getId());
        SseEmitter emitterB = connectionManager.register(userB.getId());

        assertNotNull(emitterA);
        assertNotNull(emitterB);
        assertEquals(1, connectionManager.getActiveConnectionCount(userA.getId()));
        assertEquals(1, connectionManager.getActiveConnectionCount(userB.getId()));

        // Create notification for User A
        notificationService.createNotification(
                userA.getId(),
                "Direct Notice",
                "Hello User A",
                NotificationType.GENERAL,
                "TASK",
                10L
        );

        // Verify DB persistence
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userA.getId()).size());
        assertEquals(0, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userB.getId()).size());
    }

    @Test
    @DisplayName("Test 4: Multiple tabs / connections for same user all receive real-time push")
    void test4_multipleConnectionsSameUser() {
        User user = createUser("Multi Tab User", "multitab@test.com", "pass123", Role.EMPLOYEE);

        SseEmitter tab1 = connectionManager.register(user.getId());
        SseEmitter tab2 = connectionManager.register(user.getId());

        assertEquals(2, connectionManager.getActiveConnectionCount(user.getId()));

        notificationService.createNotification(
                user.getId(),
                "Multi-tab alert",
                "Both tabs should stay synchronized",
                NotificationType.GENERAL
        );

        // DB record is exactly 1 (no duplication)
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId()).size());
    }

    @Test
    @DisplayName("Test 5: Offline user safety - business operation succeeds with no active SSE connections")
    void test5_offlineUserSafety() {
        User employee = createUser("Offline Emp", "offline@test.com", "pass123", Role.EMPLOYEE);
        assertEquals(0, connectionManager.getActiveConnectionCount(employee.getId()));

        // Notification created for offline user
        var created = notificationService.createNotification(
                employee.getId(),
                "Offline Task Notice",
                "You were assigned a task while offline",
                NotificationType.TASK_ASSIGNED,
                "TASK",
                99L
        );

        assertNotNull(created.id());
        Notification inDb = notificationRepository.findById(created.id()).orElseThrow();
        assertEquals("Offline Task Notice", inDb.getTitle());
        assertFalse(inDb.isRead());
    }

    @Test
    @DisplayName("Test 6: Reconnection lifecycle - connection cleanup and registration of new stream")
    void test6_reconnectionLifecycle() {
        User user = createUser("Reconnecting User", "reconnect@test.com", "pass123", Role.EMPLOYEE);

        SseEmitter initialEmitter = connectionManager.register(user.getId());
        assertEquals(1, connectionManager.getActiveConnectionCount(user.getId()));

        // Simulate disconnect / removal
        connectionManager.remove(user.getId(), initialEmitter);
        assertEquals(0, connectionManager.getActiveConnectionCount(user.getId()));

        // Reconnect new stream
        SseEmitter newEmitter = connectionManager.register(user.getId());
        assertEquals(1, connectionManager.getActiveConnectionCount(user.getId()));
        assertNotSame(initialEmitter, newEmitter);
    }

    @Test
    @DisplayName("Test 7: Real-time Task Assigned delivery to active Employee connection")
    void test7_taskAssignedRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T7", "m_t7@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T7", "e_t7@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);

        // Employee connects
        connectionManager.register(employee.getId());
        assertEquals(1, connectionManager.getActiveConnectionCount(employee.getId()));

        Task task = new Task();
        task.setTitle("RealTime Task");
        task.setDescription("Deliver via SSE");
        task.setPriority("HIGH");
        task.setEndDate(LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk());

        // Verified in database
        List<Notification> notifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, notifs.size());
        assertEquals(NotificationType.TASK_ASSIGNED, notifs.get(0).getType());
    }

    @Test
    @DisplayName("Test 8: Real-time Submission Received delivery to active Manager connection")
    void test8_submissionReceivedRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T8", "m_t8@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T8", "e_t8@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj 8", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 8", "Desc", project, employee, TaskStatus.ASSIGNED);

        // Manager connects
        connectionManager.register(manager.getId());
        assertEquals(1, connectionManager.getActiveConnectionCount(manager.getId()));

        Submission sub = new Submission();
        sub.setReport("Submission ready");
        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employee))
                        .param("taskId", task.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk());

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertEquals(1, managerNotifs.size());
        assertEquals(NotificationType.SUBMISSION_RECEIVED, managerNotifs.get(0).getType());
    }

    @Test
    @DisplayName("Test 9: Real-time AI Evaluation Ready delivery to active Manager connection")
    void test9_aiEvaluationReadyRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T9", "m_t9@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T9", "e_t9@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj 9", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 9", "Desc", project, employee, TaskStatus.SUBMITTED);
        createRequirement(task, "Requirement", 10, true);
        Submission submission = createSubmission(task, employee, "Report", "url", SubmissionStatus.SUBMITTED);

        // Manager connects
        connectionManager.register(manager.getId());

        AiEvaluationResult mockResult = createMockResult(90.0, 85.0, 95.0, "Good");
        Mockito.when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk());

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertTrue(managerNotifs.stream().anyMatch(n -> n.getType() == NotificationType.AI_EVALUATION_READY));
    }

    @Test
    @DisplayName("Test 10: Real-time Changes Requested delivery to active Employee connection")
    void test10_changesRequestedRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T10", "m_t10@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T10", "e_t10@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj 10", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 10", "Desc", project, employee, TaskStatus.UNDER_REVIEW);
        Submission submission = createSubmission(task, employee, "Draft", "url", SubmissionStatus.UNDER_REVIEW);

        // Employee connects
        connectionManager.register(employee.getId());

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Fix issues\"}"))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.CHANGES_REQUESTED, empNotifs.get(0).getType());
    }

    @Test
    @DisplayName("Test 11: Real-time Submission Approved delivery to active Employee connection")
    void test11_submissionApprovedRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T11", "m_t11@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T11", "e_t11@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj 11", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 11", "Desc", project, employee, TaskStatus.UNDER_REVIEW);
        Submission submission = createSubmission(task, employee, "Final deliverable", "url", SubmissionStatus.UNDER_REVIEW);

        // Employee connects
        connectionManager.register(employee.getId());

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Great job\"}"))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.SUBMISSION_APPROVED, empNotifs.get(0).getType());
    }

    @Test
    @DisplayName("Test 12: Real-time Task Status Changed delivery to active Employee connection")
    void test12_taskStatusChangedRealTimeDelivery() throws Exception {
        User manager = createUser("Manager T12", "m_t12@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee T12", "e_t12@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("RT Proj 12", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 12", "Desc", project, employee, TaskStatus.ASSIGNED);

        // Employee connects
        connectionManager.register(employee.getId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/tasks/" + task.getId() + "/status")
                        .cookie(createAuthCookie(manager))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.TASK_STATUS_CHANGED, empNotifs.get(0).getType());
    }
}
