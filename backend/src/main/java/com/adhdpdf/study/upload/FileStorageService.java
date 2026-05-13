package com.adhdpdf.study.upload;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path uploadRoot;
    private final PdfTextExtractorService pdfTextExtractorService;

    public FileStorageService(
            @Value("${study.upload.directory:uploads}") String uploadDirectory,
            PdfTextExtractorService pdfTextExtractorService) {
        this.uploadRoot = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        this.pdfTextExtractorService = pdfTextExtractorService;
    }

    @PostConstruct
    void ensureUploadDirectoryExists() throws IOException {
        Files.createDirectories(uploadRoot);
    }

    public StoredUploadResult store(MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload"
        );
        if (original.contains("..")) {
            throw new IllegalArgumentException("Invalid path in filename");
        }
        String originalName = Paths.get(original).getFileName().toString();
        String storedName = UUID.randomUUID() + "_" + originalName;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Resolved path outside upload directory");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Path relativePath = uploadRoot.getFileName().resolve(storedName);
        String relative = relativePath.toString().replace('\\', '/');

        boolean isPdf = pdfTextExtractorService.isPdf(originalName, file.getContentType());
        if (!isPdf) {
            return new StoredUploadResult(storedName, originalName, file.getSize(), relative, false, null, null);
        }

        try {
            PdfTextExtractorService.PdfExtractResult extracted = pdfTextExtractorService.extractText(target);
            return new StoredUploadResult(
                    storedName,
                    originalName,
                    file.getSize(),
                    relative,
                    true,
                    extracted.text(),
                    extracted.truncated()
            );
        } catch (IOException ex) {
            log.warn("PDF text extraction failed for {}: {}", storedName, ex.getMessage());
            return new StoredUploadResult(storedName, originalName, file.getSize(), relative, true, null, null);
        }
    }

    /**
     * Removes a stored upload file from disk if present. Path is constrained under {@link #uploadRoot}.
     */
    public void deleteStoredFileIfPresent(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return;
        }
        String clean = StringUtils.cleanPath(storedFileName);
        if (clean.contains("..")) {
            log.warn("Refusing to delete upload path with '..': {}", storedFileName);
            return;
        }
        Path target = uploadRoot.resolve(clean).normalize();
        if (!target.startsWith(uploadRoot)) {
            log.warn("Refusing to delete path outside upload root: {}", target);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}: {}", target, ex.getMessage());
        }
    }
}
