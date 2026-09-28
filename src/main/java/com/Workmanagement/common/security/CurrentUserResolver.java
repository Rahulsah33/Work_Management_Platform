package com.Workmanagement.common.security;

import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Central helper for Phase 10 security rules:
 * never trust IDs supplied by the client when the acting user
 * can be derived from the JWT (email in Authentication#getName()).
 */
@Component
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolve the authenticated user from their JWT principal (email).
     */
    public User require(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Authenticated user not found with email: " + email));
    }

    /**
     * Verify that a client-supplied employeeId actually belongs to the
     * authenticated user. If it does not match, the request is rejected.
     * Returns the canonical database user.
     */
    public User verifySelfOrThrow(String email, Long claimedUserId) {
        User user = require(email);

        if (claimedUserId != null && !user.getId().equals(claimedUserId)) {
            throw new RuntimeException(
                    "You can only perform actions for your own account");
        }

        return user;
    }

    public boolean isPrivileged(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER;
    }
}
