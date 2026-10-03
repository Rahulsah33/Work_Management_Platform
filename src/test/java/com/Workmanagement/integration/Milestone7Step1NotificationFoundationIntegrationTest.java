package com.Workmanagement.integration;

import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone7Step1NotificationFoundationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("Test 1: Authenticated user retrieves their notifications")
    void test1_authenticatedUserRetrievesNotifications() throws Exception {
        User employee = createUser("Employee Alice", "alice@notif.com", "pass123", Role.EMPLOYEE);

        createNotification(employee, "Task Assigned", "You have a new task assigned", NotificationType.TASK_ASSIGNED, "TASK", 101L);
        createNotification(employee, "Evaluation Ready", "AI Evaluation is ready", NotificationType.AI_EVALUATION_READY, "SUBMISSION", 202L);

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Evaluation Ready"))
                .andExpect(jsonPath("$[1].title").value("Task Assigned"));
    }

    @Test
    @DisplayName("Test 2: Authenticated user receives correct notification fields")
    void test2_notificationFieldsPayload() throws Exception {
        User manager = createUser("Manager Bob", "bob@notif.com", "pass123", Role.MANAGER);

        createNotification(manager, "Review Required", "Submission ready for review", NotificationType.SUBMISSION_RECEIVED, "TASK", 55L);

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].title").value("Review Required"))
                .andExpect(jsonPath("$[0].message").value("Submission ready for review"))
                .andExpect(jsonPath("$[0].type").value("SUBMISSION_RECEIVED"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].readAt").doesNotExist())
                .andExpect(jsonPath("$[0].relatedType").value("TASK"))
                .andExpect(jsonPath("$[0].relatedId").value(55));
    }

    @Test
    @DisplayName("Test 3: Unread notification count is correct on both endpoints")
    void test3_unreadNotificationCount() throws Exception {
        User employee = createUser("Employee Charlie", "charlie@notif.com", "pass123", Role.EMPLOYEE);

        createNotification(employee, "N1", "M1", NotificationType.TASK_ASSIGNED);
        createNotification(employee, "N2", "M2", NotificationType.CHANGES_REQUESTED);
        createNotification(employee, "N3", "M3", NotificationType.SUBMISSION_APPROVED);

        // Test /api/notifications/unread/count
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // Test /api/notifications/unread-count
        mockMvc.perform(get("/api/notifications/unread-count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("Test 4: User marks own notification as read")
    void test4_userMarksOwnNotificationAsRead() throws Exception {
        User employee = createUser("Employee Dave", "dave@notif.com", "pass123", Role.EMPLOYEE);
        Notification notif = createNotification(employee, "Task Update", "Task updated", NotificationType.TASK_STATUS_CHANGED, "TASK", 12L);

        mockMvc.perform(patch("/api/notifications/" + notif.getId() + "/read").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notif.getId()))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        Notification updated = notificationRepository.findById(notif.getId()).orElseThrow();
        assertTrue(updated.isRead());
        assertNotNull(updated.getReadAt());
    }

    @Test
    @DisplayName("Test 5: User marks all own notifications as read")
    void test5_userMarksAllNotificationsAsRead() throws Exception {
        User employee = createUser("Employee Eve", "eve@notif.com", "pass123", Role.EMPLOYEE);
        createNotification(employee, "N1", "M1", NotificationType.TASK_ASSIGNED);
        createNotification(employee, "N2", "M2", NotificationType.SUBMISSION_RECEIVED);

        mockMvc.perform(patch("/api/notifications/read-all").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/notifications/unread").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Test 6: Employee A cannot view Employee B notification (Isolation)")
    void test6_notificationIsolationAcrossUsers() throws Exception {
        User userA = createUser("User A", "userA@notif.com", "pass123", Role.EMPLOYEE);
        User userB = createUser("User B", "userB@notif.com", "pass123", Role.EMPLOYEE);

        createNotification(userA, "Private A", "Secret A", NotificationType.TASK_ASSIGNED);
        createNotification(userB, "Private B", "Secret B", NotificationType.TASK_ASSIGNED);

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Private A"));

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Private B"));
    }

    @Test
    @DisplayName("Test 7: Employee A cannot mark Employee B notification as read (403 Forbidden)")
    void test7_crossUserNotificationReadForbidden() throws Exception {
        User userA = createUser("User A", "userA@notif.com", "pass123", Role.EMPLOYEE);
        User userB = createUser("User B", "userB@notif.com", "pass123", Role.EMPLOYEE);

        Notification notifB = createNotification(userB, "Private B", "Secret B", NotificationType.TASK_ASSIGNED);

        mockMvc.perform(patch("/api/notifications/" + notifB.getId() + "/read").cookie(createAuthCookie(userA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        Notification inDb = notificationRepository.findById(notifB.getId()).orElseThrow();
        assertFalse(inDb.isRead());
        assertNull(inDb.getReadAt());
    }

    @Test
    @DisplayName("Test 8: Client cannot inject arbitrary recipient (Internal Service creation only)")
    void test8_recipientEnforcementOnCreation() {
        User recipient = createUser("Target User", "target@notif.com", "pass123", Role.EMPLOYEE);

        var created = notificationService.createNotification(
                recipient.getId(),
                "System Notice",
                "Internal message",
                NotificationType.GENERAL,
                "TASK",
                99L
        );

        assertNotNull(created.id());
        assertEquals("System Notice", created.title());
        assertEquals("TASK", created.relatedType());
        assertEquals(99L, created.relatedId());

        Notification inDb = notificationRepository.findById(created.id()).orElseThrow();
        assertEquals(recipient.getId(), inDb.getRecipient().getId());
    }

    @Test
    @DisplayName("Test 9: Server controls createdAt timestamp")
    void test9_serverControlsCreatedAt() {
        User user = createUser("Time User", "time@notif.com", "pass123", Role.EMPLOYEE);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        var created = notificationService.createNotification(
                user.getId(),
                "Time Check",
                "Timestamp is server generated",
                NotificationType.GENERAL
        );

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        assertNotNull(created.createdAt());
        assertTrue(created.createdAt().isAfter(before) || created.createdAt().isEqual(before));
        assertTrue(created.createdAt().isBefore(after) || created.createdAt().isEqual(after));
    }

    @Test
    @DisplayName("Test 10: Multiple roles (ADMIN, MANAGER, EMPLOYEE) use same notification infrastructure")
    void test10_multiRoleNotificationSupport() throws Exception {
        User admin = createUser("Admin", "admin@notif.com", "pass123", Role.ADMIN);
        User manager = createUser("Manager", "manager@notif.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee", "emp@notif.com", "pass123", Role.EMPLOYEE);

        createNotification(admin, "Admin Alert", "System alert", NotificationType.GENERAL);
        createNotification(manager, "Manager Alert", "Review ready", NotificationType.SUBMISSION_RECEIVED);
        createNotification(employee, "Employee Alert", "Approved", NotificationType.SUBMISSION_APPROVED);

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Admin Alert"));

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Manager Alert"));

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Employee Alert"));
    }
}
