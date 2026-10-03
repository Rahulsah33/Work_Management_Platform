package com.Workmanagement.notification.controller;

import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.model.NotificationCountResponse;
import com.Workmanagement.notification.model.NotificationPreferenceUpdateRequest;
import com.Workmanagement.notification.model.NotificationResponse;
import com.Workmanagement.notification.service.NotificationConnectionManager;
import com.Workmanagement.notification.service.NotificationPreferenceService;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Tag(name = "Notifications", description = "Endpoints for user notifications, unread counters, preferences, and real-time SSE delivery")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationConnectionManager connectionManager;
    private final CurrentUserService currentUserService;
    private final NotificationPreferenceService preferenceService;

    public NotificationController(NotificationService notificationService) {
        this(notificationService, null, null, null);
    }

    public NotificationController(
            NotificationService notificationService,
            NotificationConnectionManager connectionManager,
            CurrentUserService currentUserService
    ) {
        this(notificationService, connectionManager, currentUserService, null);
    }

    @Autowired
    public NotificationController(
            NotificationService notificationService,
            NotificationConnectionManager connectionManager,
            CurrentUserService currentUserService,
            NotificationPreferenceService preferenceService
    ) {
        this.notificationService = notificationService;
        this.connectionManager = connectionManager;
        this.currentUserService = currentUserService;
        this.preferenceService = preferenceService;
    }

    @Operation(summary = "Stream real-time notifications via SSE", description = "Establishes a Server-Sent Events (SSE) connection for the authenticated user to receive instant notification pushes.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        if (currentUserService == null || connectionManager == null) {
            throw new IllegalStateException("Real-time notification services are not configured");
        }
        User currentUser = currentUserService.getCurrentUser();
        return connectionManager.register(currentUser.getId());
    }

    @Operation(summary = "Get all notifications for authenticated user", description = "Retrieves all notifications for the currently logged-in user, ordered by creation date descending.")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    @Operation(summary = "Get unread notifications", description = "Retrieves unread notifications for the currently logged-in user.")
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getMyUnreadNotifications() {
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications());
    }

    @Operation(summary = "Get unread notification count", description = "Returns the count of unread notifications for the authenticated user.")
    @GetMapping({"/unread/count", "/unread-count"})
    public ResponseEntity<NotificationCountResponse> getMyUnreadCount() {
        return ResponseEntity.ok(new NotificationCountResponse(notificationService.getMyUnreadCount()));
    }

    @Operation(summary = "Mark single notification as read", description = "Marks a specific notification as read. Enforces that users can only mark their own notifications as read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "403", description = "Forbidden if notification belongs to another user")
    })
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @Parameter(description = "Notification ID", required = true) @PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications of the authenticated user as read.")
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @Operation(summary = "Get notification preferences", description = "Retrieves notification preferences for the authenticated user. All unconfigured categories default to true.")
    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Boolean>> getMyPreferences() {
        if (currentUserService == null || preferenceService == null) {
            throw new IllegalStateException("Preference services are not configured");
        }
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(preferenceService.getPreferencesForUser(currentUser.getId()));
    }

    @Operation(summary = "Update single notification preference", description = "Enables or disables notifications for a specific notification type for the authenticated user.")
    @PatchMapping("/preferences/{type}")
    public ResponseEntity<Map<String, Boolean>> updatePreference(
            @PathVariable String type,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request
    ) {
        if (currentUserService == null || preferenceService == null) {
            throw new IllegalStateException("Preference services are not configured");
        }
        User currentUser = currentUserService.getCurrentUser();
        NotificationType notifType;
        try {
            notifType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid notification type: " + type);
        }

        return ResponseEntity.ok(preferenceService.updatePreference(currentUser.getId(), notifType, request.getEnabled()));
    }

    @Operation(summary = "Bulk update notification preferences", description = "Updates multiple notification preferences at once for the authenticated user.")
    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Boolean>> updateAllPreferences(
            @RequestBody Map<String, Boolean> updates
    ) {
        if (currentUserService == null || preferenceService == null) {
            throw new IllegalStateException("Preference services are not configured");
        }
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(preferenceService.updateAllPreferences(currentUser.getId(), updates));
    }
}
