package com.adhdpdf.study.session;

import com.adhdpdf.study.llm.SummarizationFailureReason;

import java.util.Objects;

/**
 * One navigable slice of material: original excerpt plus an LLM summary (or a fallback string),
 * and why summarization failed when it did not succeed.
 */
public record StudySection(
        String originalContent, String summary, SummarizationFailureReason summarizationFailureReason) {

    public StudySection {
        Objects.requireNonNull(originalContent, "originalContent");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(summarizationFailureReason, "summarizationFailureReason");
    }
}
