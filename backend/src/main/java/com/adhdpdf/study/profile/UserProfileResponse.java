package com.adhdpdf.study.profile;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String anonymousId,
        String displayName,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lastActiveAt,
        UserPreferencesResponse preferences,
        ResumeTargetResponse resumeTarget) {}
