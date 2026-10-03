package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.UserToolResult;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Component
public class UserTools {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public UserTools(UserRepository userRepository, CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Tool(description = "Get an employee/user by their database ID. Return only their ID, name, email, and role.")
    @Transactional(readOnly = true)
    public UserToolResult getUser(Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (!currentUserService.isAdmin()) {
            User currentUser = currentUserService.getCurrentUser();
            if (!target.getId().equals(currentUser.getId()) && target.getRole() != Role.EMPLOYEE) {
                throw new RuntimeException("Access denied to user: " + userId);
            }
        }

        return UserToolResult.from(target);
    }

    @Tool(description = "Get all users whose role is EMPLOYEE. Use this when asked to show all employees.")
    @Transactional(readOnly = true)
    public List<UserToolResult> getAllEmployees() {
        return getUsersByRole(Role.EMPLOYEE);
    }

    @Tool(description = "Get all users matching a specific role such as EMPLOYEE, MANAGER, or ADMIN.")
    @Transactional(readOnly = true)
    public List<UserToolResult> getEmployeesByRole(String role) {
        Role requestedRole;
        try {
            requestedRole = Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException(
                    "Invalid role: " + role + ". Expected EMPLOYEE, MANAGER, or ADMIN.", ex);
        }

        if (!currentUserService.isAdmin() && requestedRole != Role.EMPLOYEE) {
            throw new RuntimeException("Access denied: Managers can only list employees.");
        }

        return getUsersByRole(requestedRole);
    }

    private List<UserToolResult> getUsersByRole(Role role) {
        return userRepository.findAll()
                .stream()
                .filter(user -> role == user.getRole())
                .map(UserToolResult::from)
                .toList();
    }
}
