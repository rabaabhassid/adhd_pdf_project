package com.adhdpdf.study.upload;

import java.util.List;

/**
 * Outcome of creating a study session after upload: id for navigation APIs, joined summaries for the upload JSON,
 * whether any LLM-eligible chunk failed, and per-chunk failure reasons for the summarized range.
 */
public record UploadSessionResult(
        String sessionId,
        String combinedSummaries,
        boolean hasFailures,
        List<ChunkSummarizationDiagnostic> summarizationDiagnostics) {}
