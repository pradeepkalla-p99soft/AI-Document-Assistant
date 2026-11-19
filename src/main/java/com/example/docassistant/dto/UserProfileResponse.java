package com.example.docassistant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        OffsetDateTime createdAt
) {}

