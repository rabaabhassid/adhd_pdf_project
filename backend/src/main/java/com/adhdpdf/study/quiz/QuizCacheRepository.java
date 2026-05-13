package com.adhdpdf.study.quiz;

import com.adhdpdf.study.session.StudySessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizCacheRepository extends JpaRepository<QuizCache, UUID> {
    Optional<QuizCache> findBySessionAndSectionIndex(StudySessionEntity session, int sectionIndex);
}
