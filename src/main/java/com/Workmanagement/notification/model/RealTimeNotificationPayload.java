package com.Workmanagement.notification.model;

public record RealTimeNotificationPayload(
        NotificationResponse notification,
        long unreadCount
) {
}
