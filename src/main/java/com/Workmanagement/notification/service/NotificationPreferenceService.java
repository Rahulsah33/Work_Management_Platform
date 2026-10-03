package com.Workmanagement.notification.service;

import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.entity.NotificationPreference;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.repository.NotificationPreferenceRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(
            NotificationPreferenceRepository preferenceRepository,
            UserRepository userRepository
    ) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Checks if a notification type is enabled for a given user.
     * Default is TRUE (enabled) if no preference record exists.
     */
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(Long userId, NotificationType type) {
        if (userId == null || type == null) {
            return true;
        }
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    /**
     * Retrieves all preferences for a user, returning a Map of NotificationType name to boolean.
     * All unconfigured types default to true.
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> getPreferencesForUser(Long userId) {
        Map<String, Boolean> preferences = new LinkedHashMap<>();
        // Default all defined types to true
        for (NotificationType type : NotificationType.values()) {
            preferences.put(type.name(), true);
        }

        if (userId != null) {
            List<NotificationPreference> userPrefs = preferenceRepository.findByUserId(userId);
            for (NotificationPreference pref : userPrefs) {
                if (pref.getType() != null) {
                    preferences.put(pref.getType().name(), pref.isEnabled());
                }
            }
        }

        return preferences;
    }

    /**
     * Updates a single notification type preference for the specified user.
     */
    @Transactional
    public Map<String, Boolean> updatePreference(Long userId, NotificationType type, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        NotificationPreference preference = preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> new NotificationPreference(user, type, enabled));

        preference.setEnabled(enabled);
        preferenceRepository.save(preference);

        return getPreferencesForUser(userId);
    }

    /**
     * Bulk updates notification preferences for the specified user.
     */
    @Transactional
    public Map<String, Boolean> updateAllPreferences(Long userId, Map<String, Boolean> updates) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (updates != null) {
            for (Map.Entry<String, Boolean> entry : updates.entrySet()) {
                try {
                    NotificationType type = NotificationType.valueOf(entry.getKey().toUpperCase());
                    boolean enabled = Boolean.TRUE.equals(entry.getValue());

                    NotificationPreference preference = preferenceRepository.findByUserIdAndType(userId, type)
                            .orElseGet(() -> new NotificationPreference(user, type, enabled));

                    preference.setEnabled(enabled);
                    preferenceRepository.save(preference);
                } catch (IllegalArgumentException ex) {
                    // Ignore or reject invalid notification types
                }
            }
        }

        return getPreferencesForUser(userId);
    }
}
