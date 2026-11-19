package com.example.docassistant.service.auth;

import com.example.docassistant.domain.UserRecord;
import com.example.docassistant.dto.AuthResponse;
import com.example.docassistant.dto.LoginRequest;
import com.example.docassistant.dto.RegisterRequest;
import com.example.docassistant.dto.UserProfileResponse;
import com.example.docassistant.exception.InvalidFileException;
import com.example.docassistant.repository.UserRepository;
import com.example.docassistant.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered: " + normalizedEmail);
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        UserRecord user = UserRecord.create(normalizedEmail, hashedPassword, request.fullName());
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        UserRecord user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        UserRecord user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}

