package com.Workmanagement.integration;

import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NotificationApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/notifications - User retrieves only their own notifications")
    void testGetNotificationsIsolation() throws Exception {
        User user1 = createUser("User 1", "u1@notif.com", "pass", Role.EMPLOYEE);
        User user2 = createUser("User 2", "u2@notif.com", "pass", Role.EMPLOYEE);

        createNotification(user1, "Notif 1", "Message 1", NotificationType.TASK_ASSIGNED);
        createNotification(user1, "Notif 2", "Message 2", NotificationType.SUBMISSION_APPROVED);
        createNotification(user2, "Notif 3", "Message 3", NotificationType.TASK_ASSIGNED);

        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Notif 2"))
                .andExpect(jsonPath("$[1].title").value("Notif 1"));
    }

    @Test
    @DisplayName("GET /api/notifications/unread/count - Returns unread notification count")
    void testGetUnreadNotificationCount() throws Exception {
        User user = createUser("User", "u@notif.com", "pass", Role.EMPLOYEE);

        createNotification(user, "Notif 1", "Msg 1", NotificationType.TASK_ASSIGNED);
        createNotification(user, "Notif 2", "Msg 2", NotificationType.SUBMISSION_RECEIVED);

        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read - User marks own notification as read; Cross-user returns 403")
    void testMarkNotificationAsRead() throws Exception {
        User user1 = createUser("User 1", "u1@notif.com", "pass", Role.EMPLOYEE);
        User user2 = createUser("User 2", "u2@notif.com", "pass", Role.EMPLOYEE);

        Notification notif2 = createNotification(user2, "Notif 2", "Msg 2", NotificationType.TASK_ASSIGNED);

        // User 1 tries to mark User 2's notification as read -> 403
        mockMvc.perform(patch("/api/notifications/" + notif2.getId() + "/read")
                        .cookie(createAuthCookie(user1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // User 2 marks own notification as read -> 200
        mockMvc.perform(patch("/api/notifications/" + notif2.getId() + "/read")
                        .cookie(createAuthCookie(user2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        Notification inDb = notificationRepository.findById(notif2.getId()).orElse(null);
        assertTrue(inDb != null && inDb.isRead());
    }

    @Test
    @DisplayName("PATCH /api/notifications/read-all - Marks all unread notifications as read for current user")
    void testMarkAllAsRead() throws Exception {
        User user = createUser("User", "u@notif.com", "pass", Role.EMPLOYEE);

        createNotification(user, "Notif 1", "Msg 1", NotificationType.TASK_ASSIGNED);
        createNotification(user, "Notif 2", "Msg 2", NotificationType.TASK_ASSIGNED);

        mockMvc.perform(patch("/api/notifications/read-all").cookie(createAuthCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
