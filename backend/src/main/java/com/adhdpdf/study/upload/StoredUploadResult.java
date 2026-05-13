package com.adhdpdf.study.upload;

/**
 * Result of saving a file and optional PDF text extraction (internal to the upload pipeline).
 */
public record StoredUploadResult(
        String storedFileName,
        String originalFileName,
        long sizeBytes,
        String relativePath,
        boolean isPdf,
        String extractedText,
        Boolean extractedTextTruncated
) {}
