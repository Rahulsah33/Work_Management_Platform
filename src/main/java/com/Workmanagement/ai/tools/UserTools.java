package com.Workmanagement.ai.tools;

import com.Workmanagement.common.dto.UserToolResult;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only AI agent tools for users/employees.
 * <p>
 * SECURITY: only id/name/email/role are returned.
 * Passwords and any authentication data are NEVER exposed.
 */
@Component
public class UserTools {

    private final UserRepository userRepository;
    private final ToolMapper toolMapper;

    public UserTools(UserRepository userRepository,
                     ToolMapper toolMapper) {
        this.userRepository = userRepository;
        this.toolMapper = toolMapper;
    }

    @Tool(description = "Get a user or employee by their ID. Returns name, email and role only.")
    public UserToolResult getEmployeeById(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + userId));

        return toolMapper.toUserToolResult(user);
    }

    @Tool(description = "Find a user or employee by their exact email address. Returns name, email and role only.")
    public UserToolResult getEmployeeByEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found with email: " + email));

        return toolMapper.toUserToolResult(user);
    }

    @Tool(description = "Get all registered users (employees, managers and admins). Returns name, email and role only.")
    public List<UserToolResult> getAllEmployees() {

        return userRepository.findAll()
                .stream()
                .map(toolMapper::toUserToolResult)
                .toList();
    }

    @Tool(description = "Get all users with a given role. Role must be one of ADMIN, MANAGER, EMPLOYEE.")
    public List<UserToolResult> getEmployeesByRole(String role) {

        Role requestedRole;
        try {
            requestedRole = Role.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(
                    "Invalid role: " + role + ". Use ADMIN, MANAGER or EMPLOYEE.");
        }

        return userRepository.findAll()
                .stream()
                .filter(u -> u.getRole() == requestedRole)
                .map(toolMapper::toUserToolResult)
                .toList();
    }
}
