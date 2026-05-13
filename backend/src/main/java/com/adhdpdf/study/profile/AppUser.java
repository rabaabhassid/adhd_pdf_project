package com.adhdpdf.study.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(unique = true, length = 320)
    private String email;

    @Column(name = "anonymous_id", unique = true, length = 128)
    private String anonymousId;

    @Column(name = "device_secret_hash", length = 128)
    private String deviceSecretHash;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    protected AppUser() {}

    public AppUser(String email, Instant now) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.lastActiveAt = now;
    }

    public static AppUser anonymousDevice(String anonymousId, String deviceSecretHash, Instant now) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.anonymousId = Objects.requireNonNull(anonymousId, "anonymousId");
        user.deviceSecretHash = Objects.requireNonNull(deviceSecretHash, "deviceSecretHash");
        user.createdAt = Objects.requireNonNull(now, "now");
        user.lastActiveAt = now;
        return user;
    }

    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getAnonymousId() {
        return anonymousId;
    }

    public String getDeviceSecretHash() {
        return deviceSecretHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null && !displayName.strip().isEmpty()
                ? displayName.strip()
                : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void markLogin(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now, "now");
        this.lastActiveAt = now;
    }

    public void markActive(Instant now) {
        this.lastActiveAt = Objects.requireNonNull(now, "now");
    }
}
