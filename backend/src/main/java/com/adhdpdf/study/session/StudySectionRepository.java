package com.adhdpdf.study.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySectionRepository extends JpaRepository<StudySectionEntity, UUID> {
    List<StudySectionEntity> findBySession_IdOrderBySectionIndexAsc(UUID sessionId);

    Optional<StudySectionEntity> findBySession_IdAndSectionIndex(UUID sessionId, int sectionIndex);
}
