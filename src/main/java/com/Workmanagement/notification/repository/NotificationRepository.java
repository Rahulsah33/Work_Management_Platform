package com.Workmanagement.notification.repository;

import com.Workmanagement.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    boolean existsByRecipientIdAndTypeAndRelatedTypeAndRelatedId(
            Long recipientId,
            com.Workmanagement.notification.entity.NotificationType type,
            String relatedType,
            Long relatedId
    );

    java.util.Optional<Notification> findFirstByRecipientIdAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(
            Long recipientId,
            com.Workmanagement.notification.entity.NotificationType type,
            String relatedType,
            Long relatedId
    );
}
