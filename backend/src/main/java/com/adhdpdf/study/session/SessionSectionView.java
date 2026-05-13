package com.adhdpdf.study.session;

import com.adhdpdf.study.llm.SummarizationFailureReason;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * JSON view for the active section and navigation state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionSectionView(
        String currentSectionText,
        String currentSectionSummary,
        SummarizationFailureReason summarizationFailureReason,
        int currentIndex,
        int totalSections,
        /** True when a multiple-choice quiz can be generated for this section (summary ok and failure reason none). */
        boolean quizAvailable,
        /** Same length as {@code totalSections}; per-section quiz availability for the roadmap. */
        List<Boolean> quizAvailableBySection
) {}
