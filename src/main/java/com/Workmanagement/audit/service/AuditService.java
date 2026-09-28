package com.Workmanagement.audit.service;

import com.Workmanagement.audit.dto.AuditLogResponse;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records important actions and exposes history for managers/admins.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // RECORD (called by other services)
    // =========================================================

    /**
     * Records an audit entry in a NEW transaction so that auditing
     * never breaks the caller's business transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId,
                       AuditAction action,
                       String entityType,
                       Long entityId,
                       String description) {

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build();

        auditLogRepository.save(log);
    }

    /**
     * Convenience overload: resolve the acting user from their email.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByEmail(String email,
                              AuditAction action,
                              String entityType,
                              Long entityId,
                              String description) {

        Long userId = userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(null);

        record(userId, action, entityType, entityId, description);
    }

    // =========================================================
    // QUERY (managers/admins)
    // =========================================================

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditHistory() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getHistoryForEntity(String entityType, Long entityId) {
        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private AuditLogResponse toResponse(AuditLog log) {
        User user = log.getUser();
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? user.getName() : "SYSTEM")
                .userEmail(user != null ? user.getEmail() : null)
                .role(user != null && user.getRole() != null ? user.getRole().name() : "SYSTEM")
                .action(log.getAction() != null ? log.getAction().name() : null)
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
