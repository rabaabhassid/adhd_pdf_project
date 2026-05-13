package com.adhdpdf.study.session;

import com.adhdpdf.study.llm.SummarizationFailureReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "study_section")
public class StudySectionEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySessionEntity session;

    @Column(name = "section_index", nullable = false)
    private int sectionIndex;

    @Column(name = "original_content", nullable = false, columnDefinition = "text")
    private String originalContent;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "summarization_failure_reason", nullable = false, length = 64)
    private String summarizationFailureReason;

    protected StudySectionEntity() {}

    public StudySectionEntity(StudySessionEntity session, int sectionIndex, StudySection section) {
        this.id = UUID.randomUUID();
        this.session = Objects.requireNonNull(session, "session");
        this.sectionIndex = sectionIndex;
        this.originalContent = section.originalContent();
        this.summary = section.summary();
        this.summarizationFailureReason = section.summarizationFailureReason().name();
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public StudySection toDomain() {
        return new StudySection(
                originalContent,
                summary,
                SummarizationFailureReason.valueOf(summarizationFailureReason));
    }
}
