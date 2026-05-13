package com.adhdpdf.study.profile;

import java.time.Instant;

public record UserStudySessionSummary(
        String sessionId,
        String originalFileName,
        int currentIndex,
        Instant lastOpenedAt,
        Instant createdAt) {}
