package com.adhdpdf.study.upload;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class PdfTextExtractorService {

    private final int maxExtractedChars;

    public PdfTextExtractorService(
            @Value("${study.pdf.max-extracted-chars:512000}") int maxExtractedChars) {
        this.maxExtractedChars = maxExtractedChars;
    }

    public boolean isPdf(String originalFilename, String contentType) {
        if (StringUtils.hasText(contentType) && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        if (!StringUtils.hasText(originalFilename)) {
            return false;
        }
        String name = originalFilename.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf");
    }

    /**
     * Reads text from a PDF on disk. Caller must only pass paths that are already validated as inside the upload area.
     */
    public PdfExtractResult extractText(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null) {
                text = "";
            }
            text = text.strip();
            if (maxExtractedChars > 0 && text.length() > maxExtractedChars) {
                return new PdfExtractResult(text.substring(0, maxExtractedChars), true);
            }
            return new PdfExtractResult(text, false);
        }
    }

    public record PdfExtractResult(String text, boolean truncated) {}
}
