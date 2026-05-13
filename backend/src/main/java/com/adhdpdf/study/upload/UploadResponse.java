package com.adhdpdf.study.upload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * JSON returned after multipart upload. When a study session is created (PDF with extract and chunks),
 * {@link #combinedSummaries()} is the same joined summaries stored on {@link com.adhdpdf.study.session.StudySession}
 * sections; {@link #sessionCurrentPath()} points at the REST view for the current section (original + summary).
 * {@link #hasFailures()} is {@code true} when any LLM-eligible chunk did not produce a summary (see
 * {@link #summarizationDiagnostics()} for per-chunk {@link com.adhdpdf.study.llm.SummarizationFailureReason}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadResponse(
        String storedFileName,
        String originalFileName,
        long sizeBytes,
        String relativePath,
        Boolean isPdf,
        /** All section summaries joined (same ordering as chunks); null if no session was created. */
        String combinedSummaries,
        String sessionId,
        /** e.g. {@code /session/{id}/current}; null when {@link #sessionId()} is null. */
        String sessionCurrentPath,
        /** True if any chunk in the configured LLM range failed to summarize; false if no session or all succeeded. */
        Boolean hasFailures,
        /** One entry per LLM-eligible chunk index with its outcome ({@link com.adhdpdf.study.llm.SummarizationFailureReason#NONE} if ok). */
        List<ChunkSummarizationDiagnostic> summarizationDiagnostics
) {

    /**
     * @param combinedSummaries joined summaries from the session pipeline; null when no session
     * @param sessionId only non-null when a session was stored (PDF with non-blank extract and at least one chunk)
     * @param hasFailures whether any LLM-eligible summary failed; ignored (stored as null) when {@code sessionId} is null
     * @param summarizationDiagnostics per-chunk reasons for the summarized range; null when no session
     */
    public static UploadResponse from(
            StoredUploadResult stored,
            String combinedSummaries,
            String sessionId,
            boolean hasFailures,
            List<ChunkSummarizationDiagnostic> summarizationDiagnostics) {
        String sessionCurrentPath =
                sessionId != null && !sessionId.isEmpty() ? "/session/" + sessionId + "/current" : null;
        boolean hasSession = sessionId != null && !sessionId.isEmpty();
        Boolean failureFlag = hasSession ? hasFailures : null;
        List<ChunkSummarizationDiagnostic> diagnostics = hasSession ? summarizationDiagnostics : null;
        return new UploadResponse(
                stored.storedFileName(),
                stored.originalFileName(),
                stored.sizeBytes(),
                stored.relativePath(),
                stored.isPdf(),
                combinedSummaries,
                sessionId,
                sessionCurrentPath,
                failureFlag,
                diagnostics);
    }
}
