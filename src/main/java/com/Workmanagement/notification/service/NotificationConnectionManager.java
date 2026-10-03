package com.Workmanagement.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(NotificationConnectionManager.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE connection for the authenticated user.
     * Supports multiple concurrent connections (e.g. multiple tabs / devices).
     *
     * @param userId The ID of the authenticated user
     * @return SseEmitter instance for streaming real-time events
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emittersByUserId.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("SSE connection established for user ID: {}. Active user connections: {}",
                userId, getActiveConnectionCount(userId));

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for user ID: {}", userId);
            remove(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for user ID: {}", userId);
            emitter.complete();
            remove(userId, emitter);
        });

        emitter.onError(ex -> {
            log.debug("SSE connection error for user ID {}: {}", userId, ex.getMessage());
            remove(userId, emitter);
        });

        // Send initial handshake event to confirm connectivity
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "message", "Notification real-time stream established"
                    )));
        } catch (IOException e) {
            log.warn("Failed to send INIT event to user {}: {}", userId, e.getMessage());
            remove(userId, emitter);
        }

        return emitter;
    }

    /**
     * Publishes a real-time notification payload to all active connections belonging to the specified user.
     * Best-effort delivery: failures are logged and dead connections are cleaned up without throwing exceptions.
     *
     * @param userId  The target recipient's user ID
     * @param payload The notification payload data
     */
    public void publish(Long userId, Object payload) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE connections for user ID: {}. Notification stored in database only.", userId);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
                log.debug("Successfully pushed real-time notification to user ID: {}", userId);
            } catch (Exception ex) {
                log.debug("Failed pushing notification to emitter for user {}: {}. Removing stale connection.",
                        userId, ex.getMessage());
                remove(userId, emitter);
            }
        }
    }

    /**
     * Removes an emitter for a user upon disconnect, timeout, or failure.
     */
    public void remove(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUserId.remove(userId, emitters);
            }
        }
    }

    /**
     * Returns the number of active SSE connections for a user.
     */
    public int getActiveConnectionCount(Long userId) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        return emitters != null ? emitters.size() : 0;
    }

    /**
     * Clears all active connections (used during test teardown).
     */
    public void clearAll() {
        emittersByUserId.clear();
    }
}
