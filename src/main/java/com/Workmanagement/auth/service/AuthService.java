package com.Workmanagement.auth.service;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.dto.AuthResponse;
import com.Workmanagement.auth.dto.RegisterRequest;
import com.Workmanagement.auth.security.JwtService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    // Register
    @Transactional
    public AuthResponse register(RegisterRequest request){
        if (userRepository.existsByEmail(request.getEmail())){
            throw new ConflictException("Email is already registered: " + request.getEmail());
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Never store plain text passwords, always encode them before saving to the database
        // Public registration always assigns EMPLOYEE role to prevent unauthorized privilege escalation
        user.setRole(Role.EMPLOYEE);

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser.getEmail());

        auditLogService.createLog(
                savedUser,
                AuditAction.USER_REGISTERED,
                "USER",
                savedUser.getId(),
                "User registered: " + savedUser.getEmail()
        );

        return new AuthResponse(
                "User registered successfully",
                token,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole() != null ? savedUser.getRole().name() : "EMPLOYEE",
                savedUser.getAvatarUrl()
        );
    }

    // Login:-
    @Transactional
    public AuthResponse loginUser(String email, String password){
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())){
            throw new BadRequestException("Invalid email or password");
        }
        String token = jwtService.generateToken(user.getEmail());

        auditLogService.createLog(
                user,
                AuditAction.USER_LOGIN,
                "USER",
                user.getId(),
                "User logged in: " + user.getEmail()
        );

        return new AuthResponse(
                "Login successful",
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : "EMPLOYEE",
                user.getAvatarUrl()
        );
    }

    public String login(String email, String password){
        return loginUser(email, password).getToken();
    }
}

