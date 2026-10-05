package com.Workmanagement.audit.model;

import com.fasterxml.jackson.annotation.JsonFormat;
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
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
}
