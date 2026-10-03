package com.Workmanagement.auth.controller;

import com.Workmanagement.auth.dto.AuthResponse;
import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.auth.dto.RegisterRequest;
import com.Workmanagement.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Authentication", description = "Endpoints for user registration and JWT authentication cookie management")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:Lax}")
    private String cookieSameSite;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account with name, email, password, and role (ADMIN, MANAGER, EMPLOYEE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User successfully registered"),
            @ApiResponse(responseCode = "400", description = "Validation failure or email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register (@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Login user and issue JWT cookie", description = "Authenticates credentials and sets an HttpOnly JWT cookie ('jwt') for subsequent session requests.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful with JWT cookie issued in Set-Cookie header"),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login (@Valid @RequestBody LoginRequest request, HttpServletResponse response) {

        AuthResponse authResponse = authService.loginUser(
                request.getEmail(),
                request.getPassword()
        );

        ResponseCookie cookie = ResponseCookie
                .from("jwt", authResponse.getToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Logout user and invalidate JWT cookie", description = "Clears the HttpOnly JWT session cookie by setting maxAge to 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful with expired JWT cookie returned")
    })
    @PostMapping("/logout")
    public ResponseEntity<java.util.Map<String, String>> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from("jwt", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        return ResponseEntity.ok(java.util.Map.of("message", "Logged out successfully"));
    }
}

