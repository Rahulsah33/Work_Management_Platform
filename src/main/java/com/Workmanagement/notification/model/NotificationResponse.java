package com.Workmanagement.notification.model;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        String relatedType,
        Long relatedId
) {
}
