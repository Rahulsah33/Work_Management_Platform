package com.Workmanagement.auth.security;

import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> getCurrentUserOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    public User getCurrentUser() {
        return getCurrentUserOptional()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public String getCurrentUserEmail() {
        return getCurrentUser().getEmail();
    }

    public boolean isAdmin() {
        return getCurrentUserOptional().map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    public boolean isManager() {
        return getCurrentUserOptional().map(u -> u.getRole() == Role.MANAGER).orElse(false);
    }

    public boolean isEmployee() {
        return getCurrentUserOptional().map(u -> u.getRole() == Role.EMPLOYEE).orElse(false);
    }
}
