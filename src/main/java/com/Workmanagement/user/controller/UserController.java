package com.Workmanagement.user.controller;

import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.common.exception.UnauthorizedException;
import com.Workmanagement.user.dto.CreateUserRequest;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import com.Workmanagement.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Users", description = "Endpoints for user profile retrieval, role verification, and directory lookups")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(
            UserRepository userRepository,
            UserService userService,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Create user with specific role", description = "Allows ADMIN to create a user with a designated role (MANAGER, EMPLOYEE, ADMIN).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Forbidden if caller is not ADMIN"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        User creator = currentUserService.getCurrentUser();
        User savedUser = userService.createUser(request, creator);
        return ResponseEntity.ok(savedUser);
    }

    @Operation(summary = "Create a manager account", description = "Allows ADMIN to explicitly create a user with MANAGER role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Forbidden if caller is not ADMIN"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createManager(@Valid @RequestBody CreateUserRequest request) {
        User creator = currentUserService.getCurrentUser();
        User savedUser = userService.createManager(request, creator);
        return ResponseEntity.ok(savedUser);
    }

    @Operation(summary = "Get current authenticated user profile", description = "Returns user profile details (id, name, email, role, avatarUrl) from the authenticated session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile retrieved"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("User is not authenticated");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("email", authentication.getName()));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        ));
    }

    @Operation(summary = "Update current user profile and photo", description = "Updates profile name and/or avatar URL for the authenticated user.")
    @PutMapping("/me/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> request) {
        User currentUser = currentUserService.getCurrentUser();
        String name = request.get("name");
        String avatarUrl = request.get("avatarUrl");
        User updated = userService.updateProfile(currentUser, name, avatarUrl);
        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "name", updated.getName(),
                "email", updated.getEmail(),
                "role", updated.getRole().name(),
                "avatarUrl", updated.getAvatarUrl() != null ? updated.getAvatarUrl() : ""
        ));
    }

    @Operation(summary = "Upload current user profile photo", description = "Uploads an image file and sets it as the avatar for authenticated user.")
    @PostMapping(value = "/me/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        User currentUser = currentUserService.getCurrentUser();
        if (file.isEmpty()) {
            throw new com.Workmanagement.common.exception.BadRequestException("Uploaded file is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.Workmanagement.common.exception.BadRequestException("File size exceeds 5MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new com.Workmanagement.common.exception.BadRequestException("Only image files (JPEG, PNG, WebP, GIF) are allowed");
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
        String dataUrl = "data:" + contentType + ";base64," + base64;
        User updated = userService.updateProfile(currentUser, null, dataUrl);
        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "name", updated.getName(),
                "email", updated.getEmail(),
                "role", updated.getRole().name(),
                "avatarUrl", updated.getAvatarUrl() != null ? updated.getAvatarUrl() : ""
        ));
    }

    @Operation(summary = "Get all users", description = "Retrieves all users across the system. Requires ADMIN or MANAGER role.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @Operation(summary = "Get all employees", description = "Retrieves all users with EMPLOYEE role. Requires ADMIN or MANAGER role.")
    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<User>> getEmployees() {
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE)
                .collect(Collectors.toList());
        return ResponseEntity.ok(employees);
    }

    @Operation(summary = "Get all managers and admins", description = "Retrieves all users with MANAGER or ADMIN role. Requires ADMIN or MANAGER role.")
    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<User>> getManagers() {
        List<User> managers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.MANAGER || u.getRole() == Role.ADMIN)
                .collect(Collectors.toList());
        return ResponseEntity.ok(managers);
    }

    @Operation(summary = "Verify ADMIN role access", description = "Endpoint to verify that authenticated user has ADMIN authority.")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "Welcome ADMIN";
    }

    @Operation(summary = "Verify MANAGER role access", description = "Endpoint to verify that authenticated user has MANAGER authority.")
    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerOnly() {
        return "Logged in as manager";
    }

    @Operation(summary = "Verify EMPLOYEE role access", description = "Endpoint to verify that authenticated user has EMPLOYEE authority.")
    @GetMapping("/employee")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public String employeeOnly() {
        return "Logged in as employee";
    }

    @Operation(summary = "Delete user by ID", description = "Allows ADMIN to permanently delete a user account and cleanly cascade clean child entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Self-deletion or last admin deletion rejected"),
            @ApiResponse(responseCode = "403", description = "Forbidden if caller is not ADMIN"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User currentUser = currentUserService.getCurrentUser();
        userService.deleteUser(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}

