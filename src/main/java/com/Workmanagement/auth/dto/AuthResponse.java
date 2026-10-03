package com.Workmanagement.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AuthResponse {
    private String message;
    private String token;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String avatarUrl;

    public AuthResponse(String message) {
        this.message = message;
    }

    public AuthResponse(String message, String token) {
        this.message = message;
        this.token = token;
    }

    public AuthResponse(String message, String token, Long userId, String name, String email, String role) {
        this.message = message;
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public AuthResponse(String message, String token, Long userId, String name, String email, String role, String avatarUrl) {
        this.message = message;
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.avatarUrl = avatarUrl;
    }
}

