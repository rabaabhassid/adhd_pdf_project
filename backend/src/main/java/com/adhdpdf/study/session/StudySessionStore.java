package com.adhdpdf.study.session;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.upload.UploadedFile;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Database-backed session store scoped by user ownership.
 */
@Service
public class StudySessionStore {

    private final StudySessionRepository studySessionRepository;
    private final StudySectionRepository studySectionRepository;

    public StudySessionStore(StudySessionRepository studySessionRepository, StudySectionRepository studySectionRepository) {
        this.studySessionRepository = studySessionRepository;
        this.studySectionRepository = studySectionRepository;
    }

    @Transactional
    public void save(AppUser user, UploadedFile upload, StudySession session) {
        Instant now = Instant.now();
        StudySessionEntity savedSession = studySessionRepository.save(new StudySessionEntity(
                session.getId(), user, upload, session.getCurrentIndex(), now));
        List<StudySectionEntity> sections = new ArrayList<>(session.getSections().size());
        for (int i = 0; i < session.getSections().size(); i++) {
            sections.add(new StudySectionEntity(savedSession, i, session.getSections().get(i)));
        }
        studySectionRepository.saveAll(sections);
    }

    @Transactional
    public Optional<StudySession> findByIdForUser(String sessionId, AppUser user) {
        return findEntityForUser(sessionId, user).map(entity -> {
            List<StudySection> sections = studySectionRepository.findBySession_IdOrderBySectionIndexAsc(entity.getId())
                    .stream()
                    .map(StudySectionEntity::toDomain)
                    .toList();
            entity.markOpened(Instant.now());
            return new StudySession(entity.getPublicId(), sections, entity.getCurrentIndex());
        });
    }

    @Transactional
    public Optional<StudySessionEntity> findEntityForUser(String sessionId, AppUser user) {
        return studySessionRepository.findByPublicIdAndUser_Id(sessionId, user.getId());
    }

    @Transactional
    public void updateCurrentIndexForUser(String sessionId, AppUser user, int currentIndex) {
        studySessionRepository.findByPublicIdAndUser_Id(sessionId, user.getId())
                .ifPresent(session -> session.setCurrentIndex(currentIndex, Instant.now()));
    }
}
