package com.Workmanagement.notification.service;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.model.NotificationResponse;
import com.Workmanagement.notification.model.RealTimeNotificationPayload;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationConnectionManager connectionManager;
    private final NotificationPreferenceService preferenceService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this(notificationRepository, userRepository, auditLogService, null, null);
    }

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            NotificationConnectionManager connectionManager
    ) {
        this(notificationRepository, userRepository, auditLogService, connectionManager, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            NotificationConnectionManager connectionManager,
            NotificationPreferenceService preferenceService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.connectionManager = connectionManager;
        this.preferenceService = preferenceService;
    }

    // =========================================================
    // CREATE NOTIFICATION (Internal use across services)
    // =========================================================
    @Transactional
    public NotificationResponse createNotification(
            Long recipientId,
            String title,
            String message,
            NotificationType type
    ) {
        return createNotification(recipientId, title, message, type, null, null);
    }

    @Transactional
    public NotificationResponse createNotification(
            Long recipientId,
            String title,
            String message,
            NotificationType type,
            String relatedType,
            Long relatedId
    ) {
        // Enforce user notification preference: if category is disabled, do not create or deliver
        if (preferenceService != null && !preferenceService.isNotificationEnabled(recipientId, type)) {
            return null;
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + recipientId));

        if (relatedType != null && relatedId != null) {
            var existing = notificationRepository.findFirstByRecipientIdAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(
                    recipientId, type, relatedType, relatedId
            );
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setRecipient(recipient);

        Notification saved = notificationRepository.save(notification);

        auditLogService.createLog(
                AuditAction.NOTIFICATION_CREATED,
                "NOTIFICATION",
                saved.getId(),
                "Created notification: " + title + " for user ID: " + recipientId
        );

        NotificationResponse response = mapToResponse(saved);

        // Deliver real-time notification to active connections (best-effort delivery)
        if (connectionManager != null) {
            try {
                long unreadCount = notificationRepository.countByRecipientIdAndReadFalse(recipient.getId());
                RealTimeNotificationPayload payload = new RealTimeNotificationPayload(response, unreadCount);
                connectionManager.publish(recipient.getId(), payload);
            } catch (Exception ex) {
                // Best-effort delivery: failures must never break the underlying business operation
            }
        }

        return response;
    }

    // =========================================================
    // GET CURRENT USER'S NOTIFICATIONS
    // =========================================================
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User currentUser = getAuthenticatedUser();
        return notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(currentUser.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // =========================================================
    // GET CURRENT USER'S UNREAD NOTIFICATIONS
    // =========================================================
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyUnreadNotifications() {
        User currentUser = getAuthenticatedUser();
        return notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(currentUser.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // =========================================================
    // GET UNREAD COUNT
    // =========================================================
    @Transactional(readOnly = true)
    public long getMyUnreadCount() {
        User currentUser = getAuthenticatedUser();
        return notificationRepository.countByRecipientIdAndReadFalse(currentUser.getId());
    }

    // =========================================================
    // MARK ONE AS READ
    // =========================================================
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        User currentUser = getAuthenticatedUser();

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied: You can only mark your own notifications as read");
        }

        notification.setRead(true);
        notification.setReadAt(java.time.LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    // =========================================================
    // MARK ALL AS READ
    // =========================================================
    @Transactional
    public void markAllAsRead() {
        User currentUser = getAuthenticatedUser();
        List<Notification> unreadList = notificationRepository
                .findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(currentUser.getId());

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Notification notification : unreadList) {
            notification.setRead(true);
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unreadList);
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType() != null ? notification.getType().name() : null,
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getRelatedType(),
                notification.getRelatedId()
        );
    }
}
