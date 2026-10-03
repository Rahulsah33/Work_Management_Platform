package com.Workmanagement.audit.model;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long performedById,
        String performedByName,
        String performedByEmail,
        String action,
        String entityType,
        Long entityId,
        String description,
        LocalDateTime createdAt
) {
}
