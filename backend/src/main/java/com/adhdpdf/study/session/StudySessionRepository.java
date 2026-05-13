package com.adhdpdf.study.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySessionEntity, UUID> {
    Optional<StudySessionEntity> findByPublicIdAndUser_Id(String publicId, UUID userId);

    Optional<StudySessionEntity> findFirstByUser_IdOrderByLastOpenedAtDesc(UUID userId);

    List<StudySessionEntity> findTop20ByUser_IdOrderByLastOpenedAtDesc(UUID userId);

    /**
     * Latest study session whose source upload used this extracted-text fingerprint (same PDF text for this user).
     */
    Optional<StudySessionEntity> findFirstByUser_IdAndUpload_ContentTextHashOrderByCreatedAtDesc(
            UUID userId, String contentTextHash);

    boolean existsByUpload_Id(UUID uploadId);
}
