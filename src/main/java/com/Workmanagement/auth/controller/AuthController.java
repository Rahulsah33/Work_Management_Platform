package com.Workmanagement.auth.controller;

import com.Workmanagement.auth.dto.AuthResponse;
import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.auth.dto.RegisterRequest;
import com.Workmanagement.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")   // localhost:8080/api/auth/register
    public ResponseEntity<AuthResponse> register (@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login (@Valid @RequestBody LoginRequest request, HttpServletResponse response) {

        String token = authService.login(
                request.getEmail(),
                request.getPassword()
        );

        ResponseCookie cookie = ResponseCookie
                .from("jwt", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();


        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        return  ResponseEntity.ok(new AuthResponse("Login successful"));
    }
}

