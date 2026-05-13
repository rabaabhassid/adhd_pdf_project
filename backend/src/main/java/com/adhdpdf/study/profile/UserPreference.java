package com.adhdpdf.study.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_preference")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 32)
    private String theme;

    @Column(name = "text_size", nullable = false, length = 32)
    private String textSize;

    @Column(name = "preferred_study_landing", nullable = false, length = 64)
    private String preferredStudyLanding;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {}

    public UserPreference(AppUser user, Instant now) {
        this.user = Objects.requireNonNull(user, "user");
        this.theme = "teal";
        this.textSize = "comfortable";
        this.preferredStudyLanding = "resume";
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public AppUser getUser() {
        return user;
    }

    public String getTheme() {
        return theme;
    }

    public String getTextSize() {
        return textSize;
    }

    public String getPreferredStudyLanding() {
        return preferredStudyLanding;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String theme, String textSize, String preferredStudyLanding, Instant now) {
        if (theme != null && !theme.isBlank()) {
            this.theme = theme.strip();
        }
        if (textSize != null && !textSize.isBlank()) {
            this.textSize = textSize.strip();
        }
        if (preferredStudyLanding != null && !preferredStudyLanding.isBlank()) {
            this.preferredStudyLanding = preferredStudyLanding.strip();
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
