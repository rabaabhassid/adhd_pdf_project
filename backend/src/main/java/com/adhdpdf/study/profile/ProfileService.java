package com.adhdpdf.study.profile;

import com.adhdpdf.study.session.StudySessionEntity;
import com.adhdpdf.study.session.StudySessionRepository;
import com.adhdpdf.study.upload.FileStorageService;
import com.adhdpdf.study.upload.UploadedFile;
import com.adhdpdf.study.upload.UploadedFileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private final AppUserRepository appUserRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final StudySessionRepository studySessionRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;

    public ProfileService(
            AppUserRepository appUserRepository,
            UserPreferenceRepository userPreferenceRepository,
            StudySessionRepository studySessionRepository,
            UploadedFileRepository uploadedFileRepository,
            FileStorageService fileStorageService) {
        this.appUserRepository = appUserRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.studySessionRepository = studySessionRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public UserPreference ensurePreferences(AppUser user) {
        return userPreferenceRepository.findById(user.getId())
                .orElseGet(() -> {
                    AppUser managedUser = appUserRepository.findById(user.getId()).orElseThrow();
                    return userPreferenceRepository.save(new UserPreference(managedUser, Instant.now()));
                });
    }

    @Transactional
    public UserProfileResponse profile(AppUser user) {
        UserPreference preference = ensurePreferences(user);
        ResumeTargetResponse resumeTarget = studySessionRepository.findFirstByUser_IdOrderByLastOpenedAtDesc(user.getId())
                .map(this::toResumeTarget)
                .orElse(null);
        return toResponse(user, preference, resumeTarget);
    }

    @Transactional
    public UserProfileResponse updateProfile(AppUser user, UpdateProfileRequest request) {
        AppUser managedUser = appUserRepository.findById(user.getId()).orElseThrow();
        managedUser.setDisplayName(request != null ? request.displayName() : null);
        return profile(managedUser);
    }

    @Transactional
    public UserProfileResponse updatePreferences(AppUser user, UpdatePreferencesRequest request) {
        UserPreference preference = ensurePreferences(user);
        preference.update(
                request != null ? request.theme() : null,
                request != null ? request.textSize() : null,
                request != null ? request.preferredStudyLanding() : null,
                Instant.now());
        return profile(user);
    }

    @Transactional
    public List<UserStudySessionSummary> recentSessions(AppUser user) {
        return studySessionRepository.findTop20ByUser_IdOrderByLastOpenedAtDesc(user.getId()).stream()
                .map(session -> new UserStudySessionSummary(
                        session.getPublicId(),
                        session.getUpload() != null ? session.getUpload().getOriginalFileName() : "Study session",
                        session.getCurrentIndex(),
                        session.getLastOpenedAt(),
                        session.getCreatedAt()))
                .toList();
    }

    /**
     * Deletes the study session (sections, quiz cache), then the uploaded file row and on-disk file when no other
     * session references that upload. Returns false if the session does not exist or is not owned by the user.
     */
    @Transactional
    public boolean deleteStudySession(AppUser user, String sessionPublicId) {
        return studySessionRepository
                .findByPublicIdAndUser_Id(sessionPublicId, user.getId())
                .map(session -> {
                    UploadedFile upload = session.getUpload();
                    UUID uploadId = null;
                    String storedFileName = null;
                    if (upload != null) {
                        uploadId = upload.getId();
                        storedFileName = upload.getStoredFileName();
                    }
                    studySessionRepository.delete(session);
                    studySessionRepository.flush();
                    if (uploadId != null && !studySessionRepository.existsByUpload_Id(uploadId)) {
                        uploadedFileRepository.deleteById(uploadId);
                        fileStorageService.deleteStoredFileIfPresent(storedFileName);
                    }
                    return true;
                })
                .orElse(false);
    }

    private UserProfileResponse toResponse(AppUser user, UserPreference preference, ResumeTargetResponse resumeTarget) {
        return new UserProfileResponse(
                user.getId(),
                user.getAnonymousId(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getLastActiveAt(),
                new UserPreferencesResponse(
                        preference.getTheme(),
                        preference.getTextSize(),
                        preference.getPreferredStudyLanding(),
                        preference.getUpdatedAt()),
                resumeTarget);
    }

    private ResumeTargetResponse toResumeTarget(StudySessionEntity session) {
        String label = session.getUpload() != null ? session.getUpload().getOriginalFileName() : "Continue studying";
        return new ResumeTargetResponse(session.getPublicId(), session.getCurrentIndex(), label);
    }
}
