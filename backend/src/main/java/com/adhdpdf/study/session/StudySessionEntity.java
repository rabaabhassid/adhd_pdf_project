package com.adhdpdf.study.session;

import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.upload.UploadedFile;
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
@Table(name = "study_session")
public class StudySessionEntity {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne
    @JoinColumn(name = "upload_id")
    private UploadedFile upload;

    @Column(name = "current_index", nullable = false)
    private int currentIndex;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_opened_at", nullable = false)
    private Instant lastOpenedAt;

    protected StudySessionEntity() {}

    public StudySessionEntity(String publicId, AppUser user, UploadedFile upload, int currentIndex, Instant now) {
        this.id = UUID.randomUUID();
        this.publicId = Objects.requireNonNull(publicId, "publicId");
        this.user = Objects.requireNonNull(user, "user");
        this.upload = upload;
        this.currentIndex = currentIndex;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
        this.lastOpenedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public UploadedFile getUpload() {
        return upload;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setCurrentIndex(int currentIndex, Instant now) {
        this.currentIndex = currentIndex;
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.lastOpenedAt = now;
    }

    public void markOpened(Instant now) {
        this.lastOpenedAt = Objects.requireNonNull(now, "now");
    }
}
