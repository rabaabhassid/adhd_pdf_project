package com.adhdpdf.study.upload;

import com.adhdpdf.study.auth.CurrentUserService;
import com.adhdpdf.study.profile.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
public class UploadController {

    private final FileStorageService fileStorageService;
    private final UploadStudySessionService uploadStudySessionService;
    private final CurrentUserService currentUserService;
    private final UploadedFileRepository uploadedFileRepository;

    public UploadController(
            FileStorageService fileStorageService,
            UploadStudySessionService uploadStudySessionService,
            CurrentUserService currentUserService,
            UploadedFileRepository uploadedFileRepository) {
        this.fileStorageService = fileStorageService;
        this.uploadStudySessionService = uploadStudySessionService;
        this.currentUserService = currentUserService;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    /** Legacy URL: send users to the static home page at {@code /}. */
    @GetMapping("/upload")
    public ResponseEntity<Void> uploadPageRedirect() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        AppUser user = currentUserService.requireUser();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            StoredUploadResult stored = fileStorageService.store(file);
            String contentHash =
                    stored.isPdf() && StringUtils.hasText(stored.extractedText())
                            ? ContentFingerprint.sha256Hex(stored.extractedText())
                            : null;
            UploadedFile upload =
                    uploadedFileRepository.save(new UploadedFile(user, stored, Instant.now(), contentHash));
            String combinedSummaries = null;
            String sessionId = null;
            boolean hasFailures = false;
            List<ChunkSummarizationDiagnostic> summarizationDiagnostics = null;
            if (stored.isPdf() && StringUtils.hasText(stored.extractedText())) {
                Optional<UploadSessionResult> sessionOutcome =
                        uploadStudySessionService.createSessionWithSummaries(user, upload, stored.extractedText());
                if (sessionOutcome.isPresent()) {
                    UploadSessionResult r = sessionOutcome.get();
                    sessionId = r.sessionId();
                    combinedSummaries = r.combinedSummaries();
                    hasFailures = r.hasFailures();
                    summarizationDiagnostics = r.summarizationDiagnostics();
                }
            }
            UploadResponse body =
                    UploadResponse.from(stored, combinedSummaries, sessionId, hasFailures, summarizationDiagnostics);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
