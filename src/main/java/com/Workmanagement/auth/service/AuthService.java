package com.Workmanagement.auth.service;

import com.Workmanagement.auth.dto.AuthResponse;
import com.Workmanagement.auth.dto.RegisterRequest;
import com.Workmanagement.auth.security.JwtService;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;

    }

    // Register
    public AuthResponse register(RegisterRequest request){
        if (userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email already Registered");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        // Never store plain text passwords, always encode them before saving to the database
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(request.getRole());

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());

        return new AuthResponse("User registered successfully");
    }

    // Login:-
    public String login(String email, String password){

        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())){
            throw new RuntimeException("Invalid email or password");
        }
        return jwtService.generateToken(user.getEmail());
    }

}
