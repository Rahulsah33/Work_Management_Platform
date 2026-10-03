package com.Workmanagement.ai.model;

import com.Workmanagement.user.entity.User;

public record UserToolResult(
        Long id,
        String name,
        String email,
        String role
) {

    public static UserToolResult from(User user) {
        return new UserToolResult(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole() == null ? null : user.getRole().name()
        );
    }
}
