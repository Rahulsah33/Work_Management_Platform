package com.Workmanagement.notification.repository;

import com.Workmanagement.notification.entity.NotificationPreference;
import com.Workmanagement.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndType(Long userId, NotificationType type);

    boolean existsByUserIdAndTypeAndEnabledFalse(Long userId, NotificationType type);
}
