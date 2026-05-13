package com.adhdpdf.study.upload;

import com.adhdpdf.study.llm.SummarizationFailureReason;

/**
 * Per-chunk summarization outcome for API responses (0-based chunk index in the study session).
 */
public record ChunkSummarizationDiagnostic(int chunkIndex, SummarizationFailureReason reason) {}
