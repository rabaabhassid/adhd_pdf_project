package com.adhdpdf.study.profile;

import java.time.Instant;

public record UserPreferencesResponse(
        String theme,
        String textSize,
        String preferredStudyLanding,
        Instant updatedAt) {}
