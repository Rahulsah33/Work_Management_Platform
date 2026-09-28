package com.Workmanagement.notification.service;

import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.dto.NotificationResponse;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // CREATE (internal API used by other services)
    // =========================================================

    @Transactional
    public Notification createNotification(Long userId,
                                           String title,
                                           String message,
                                           NotificationType type) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .build();

        return notificationRepository.save(notification);
    }

    // =========================================================
    // LIST FOR AUTHENTICATED USER
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String email) {

        User user = resolveUser(email);

        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {

        User user = resolveUser(email);
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    // =========================================================
    // MARK READ
    // =========================================================

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, String email) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        // Ownership check: a user may only modify their own notifications.
        User user = resolveUser(email);
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only update your own notifications");
        }

        notification.setIsRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(String email) {

        User user = resolveUser(email);
        return notificationRepository.markAllReadForUser(user.getId());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with email: " + email));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType() != null ? n.getType().name() : null)
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
