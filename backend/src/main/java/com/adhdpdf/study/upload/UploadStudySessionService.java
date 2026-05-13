package com.adhdpdf.study.upload;

import com.adhdpdf.study.llm.ChunkSummarizationOutcome;
import com.adhdpdf.study.llm.LlmSummarizationService;
import com.adhdpdf.study.llm.SummarizationFailureReason;
import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.session.StudySectionEntity;
import com.adhdpdf.study.session.StudySectionRepository;
import com.adhdpdf.study.session.StudySection;
import com.adhdpdf.study.session.StudySession;
import com.adhdpdf.study.session.StudySessionEntity;
import com.adhdpdf.study.session.StudySessionRepository;
import com.adhdpdf.study.session.StudySessionStore;
import com.adhdpdf.study.text.TextChunk;
import com.adhdpdf.study.text.TextChunkingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One chunking pass: builds {@link StudySection}s (original + OpenRouter summary), stores {@link StudySession},
 * and produces joined summaries returned as {@link UploadSessionResult#combinedSummaries()} (also exposed on
 * {@link UploadResponse#combinedSummaries()}). Summaries for the first
 * {@code study.openrouter.max-chunks-per-upload} chunks are processed by OpenRouter through the adaptive pipeline:
 * step 1 simplifies each chunk, then step 2 reorganizes simplified material into meaning-based study sections.
 */
@Service
public class UploadStudySessionService {

    private static final Logger log = LoggerFactory.getLogger(UploadStudySessionService.class);

    private static final String SUMMARY_UNAVAILABLE = "Summary unavailable";

    private final TextChunkingService textChunkingService;
    private final StudySessionStore studySessionStore;
    private final StudySessionRepository studySessionRepository;
    private final StudySectionRepository studySectionRepository;
    private final LlmSummarizationService llmSummarizationService;
    private final int maxLlmChunksPerUpload;

    public UploadStudySessionService(
            TextChunkingService textChunkingService,
            StudySessionStore studySessionStore,
            StudySessionRepository studySessionRepository,
            StudySectionRepository studySectionRepository,
            LlmSummarizationService llmSummarizationService,
            @Value("${study.openrouter.max-chunks-per-upload:50}") int maxLlmChunksPerUpload) {
        this.textChunkingService = textChunkingService;
        this.studySessionStore = studySessionStore;
        this.studySessionRepository = studySessionRepository;
        this.studySectionRepository = studySectionRepository;
        this.llmSummarizationService = llmSummarizationService;
        this.maxLlmChunksPerUpload = maxLlmChunksPerUpload;
    }

    /**
     * Blank extract → no session. Otherwise chunks with {@link TextChunkingService#splitIntoChunks(String)},
     * summarizes up to the configured cap; tail chunks keep originals with
     * "Summary unavailable" and {@link SummarizationFailureReason#NOT_SUMMARIZED_OVER_CAP}.
     */
    public Optional<UploadSessionResult> createSessionWithSummaries(
            AppUser user, UploadedFile upload, String extractedText) {
        if (!StringUtils.hasText(extractedText)) {
            return Optional.empty();
        }
        List<TextChunk> chunks = textChunkingService.splitIntoChunks(extractedText);
        if (chunks.isEmpty()) {
            return Optional.empty();
        }

        Optional<UploadSessionResult> cached = tryReusePriorSummaries(user, upload);
        if (cached.isPresent()) {
            return cached;
        }

        int llmCap = maxLlmChunksPerUpload <= 0 ? Integer.MAX_VALUE : maxLlmChunksPerUpload;
        int summarizeCount = Math.min(chunks.size(), llmCap);

        Optional<LlmSummarizationService.AdaptiveSessionSummarizationResult> adaptive =
                llmSummarizationService.summarizeAdaptiveSession(chunks.subList(0, summarizeCount));
        if (adaptive.isEmpty()) {
            return Optional.empty();
        }
        return buildFromAdaptiveResult(user, upload, chunks, summarizeCount, adaptive.get());
    }

    private Optional<UploadSessionResult> buildFromAdaptiveResult(
            AppUser user,
            UploadedFile upload,
            List<TextChunk> chunks,
            int summarizeCount,
            LlmSummarizationService.AdaptiveSessionSummarizationResult adaptive) {
        List<ChunkSummarizationOutcome> outcomes = adaptive.chunkOutcomes();
        List<ChunkSummarizationDiagnostic> diagnostics = new ArrayList<>(summarizeCount);
        boolean hasFailures = false;
        for (int i = 0; i < summarizeCount; i++) {
            ChunkSummarizationOutcome outcome =
                    i < outcomes.size()
                            ? outcomes.get(i)
                            : ChunkSummarizationOutcome.fail(SummarizationFailureReason.UNKNOWN);
            SummarizationFailureReason reason =
                    outcome.succeeded() ? SummarizationFailureReason.NONE : outcome.failureReason();
            diagnostics.add(new ChunkSummarizationDiagnostic(i, reason));
            if (reason != SummarizationFailureReason.NONE) {
                hasFailures = true;
            }
        }

        List<StudySection> sections = new ArrayList<>();
        List<String> adaptiveSummaries = adaptive.sectionSummaries();
        if (adaptiveSummaries != null && !adaptiveSummaries.isEmpty()) {
            for (String summary : adaptiveSummaries) {
                String s = StringUtils.hasText(summary) ? summary : SUMMARY_UNAVAILABLE;
                sections.add(new StudySection(s, s, SummarizationFailureReason.NONE));
            }
        } else {
            for (int i = 0; i < summarizeCount; i++) {
                ChunkSummarizationOutcome outcome =
                        i < outcomes.size()
                                ? outcomes.get(i)
                                : ChunkSummarizationOutcome.fail(SummarizationFailureReason.UNKNOWN);
                String original = chunks.get(i).content();
                String summary = outcome.summary().orElse(SUMMARY_UNAVAILABLE);
                SummarizationFailureReason reason =
                        outcome.succeeded() ? SummarizationFailureReason.NONE : outcome.failureReason();
                sections.add(new StudySection(original, summary, reason));
            }
        }

        for (int i = summarizeCount; i < chunks.size(); i++) {
            sections.add(new StudySection(
                    chunks.get(i).content(),
                    SUMMARY_UNAVAILABLE,
                    SummarizationFailureReason.NOT_SUMMARIZED_OVER_CAP));
        }

        StudySession session = StudySession.fromStudySections(sections);
        studySessionStore.save(user, upload, session);
        String combined =
                sections.stream().map(StudySection::summary).collect(Collectors.joining("\n\n---\n\n"));
        return Optional.of(new UploadSessionResult(session.getId(), combined, hasFailures, List.copyOf(diagnostics)));
    }

    /**
     * If this user already has a study session built from the same extracted text (SHA-256 on upload row),
     * clone those sections into a new session and skip OpenRouter.
     */
    private Optional<UploadSessionResult> tryReusePriorSummaries(AppUser user, UploadedFile upload) {
        String hash = upload.getContentTextHash();
        if (!StringUtils.hasText(hash)) {
            return Optional.empty();
        }
        Optional<StudySessionEntity> priorOpt =
                studySessionRepository.findFirstByUser_IdAndUpload_ContentTextHashOrderByCreatedAtDesc(
                        user.getId(), hash);
        if (priorOpt.isEmpty()) {
            return Optional.empty();
        }
        StudySessionEntity prior = priorOpt.get();
        if (prior.getUpload() != null && prior.getUpload().getId().equals(upload.getId())) {
            return Optional.empty();
        }

        List<StudySectionEntity> priorRows =
                studySectionRepository.findBySession_IdOrderBySectionIndexAsc(prior.getId());
        if (priorRows.isEmpty()) {
            return Optional.empty();
        }

        List<StudySection> sections =
                priorRows.stream().map(StudySectionEntity::toDomain).toList();

        StudySession session = StudySession.fromStudySections(sections);
        studySessionStore.save(user, upload, session);
        String combined =
                sections.stream().map(StudySection::summary).collect(Collectors.joining("\n\n---\n\n"));
        boolean hasFailures =
                sections.stream().anyMatch(s -> s.summarizationFailureReason() != SummarizationFailureReason.NONE);

        log.info(
                "Reused stored summaries for user {} (content hash {}…), new session {}; skipped LLM.",
                user.getId(),
                hash.length() >= 12 ? hash.substring(0, 12) : hash,
                session.getId());

        return Optional.of(new UploadSessionResult(session.getId(), combined, hasFailures, null));
    }
}
