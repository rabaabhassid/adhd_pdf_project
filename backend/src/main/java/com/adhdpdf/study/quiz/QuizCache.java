package com.adhdpdf.study.quiz;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.session.StudySessionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quiz_cache")
public class QuizCache {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySessionEntity session;

    @Column(name = "section_index", nullable = false)
    private int sectionIndex;

    @Column(name = "questions_json", nullable = false, columnDefinition = "text")
    private String questionsJson;

    @Column(name = "last_correct_count")
    private Integer lastCorrectCount;

    @Column(name = "last_total_count")
    private Integer lastTotalCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizCache() {}

    public QuizCache(AppUser user, StudySessionEntity session, int sectionIndex, String questionsJson, Instant now) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user");
        this.session = Objects.requireNonNull(session, "session");
        this.sectionIndex = sectionIndex;
        this.questionsJson = Objects.requireNonNull(questionsJson, "questionsJson");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public String getQuestionsJson() {
        return questionsJson;
    }

    public void recordScore(int correctCount, int totalCount, Instant now) {
        this.lastCorrectCount = correctCount;
        this.lastTotalCount = totalCount;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
