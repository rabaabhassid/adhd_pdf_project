package com.adhdpdf.study.llm;

import java.util.Optional;

/**
 * Result of summarizing one chunk. On success, {@link #failureReason()} is {@link SummarizationFailureReason#NONE}
 * and {@link #summary()} is present.
 */
public record ChunkSummarizationOutcome(SummarizationFailureReason failureReason, Optional<String> summary) {

    public static ChunkSummarizationOutcome ok(String text) {
        return new ChunkSummarizationOutcome(SummarizationFailureReason.NONE, Optional.of(text));
    }

    public static ChunkSummarizationOutcome fail(SummarizationFailureReason reason) {
        if (reason == SummarizationFailureReason.NONE) {
            throw new IllegalArgumentException("use ok() for successful outcomes");
        }
        return new ChunkSummarizationOutcome(reason, Optional.empty());
    }

    public boolean succeeded() {
        return failureReason == SummarizationFailureReason.NONE && summary.isPresent();
    }
}
