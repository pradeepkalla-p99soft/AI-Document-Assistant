package com.example.docassistant.service.auth;

import com.example.docassistant.dto.AuthResponse;
import com.example.docassistant.dto.LoginRequest;
import com.example.docassistant.dto.RegisterRequest;
import com.example.docassistant.dto.UserProfileResponse;

import java.util.UUID;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserProfileResponse getCurrentUserProfile(UUID userId);
}

