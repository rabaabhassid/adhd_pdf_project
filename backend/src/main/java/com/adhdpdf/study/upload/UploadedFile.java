package com.adhdpdf.study.upload;

import com.adhdpdf.study.profile.AppUser;
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
@Table(name = "uploaded_file")
public class UploadedFile {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "stored_file_name", nullable = false, length = 512)
    private String storedFileName;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "is_pdf", nullable = false)
    private boolean pdf;

    @Column(name = "extracted_text_truncated")
    private Boolean extractedTextTruncated;

    /** SHA-256 hex of {@code extractedText} when known; used to reuse prior summaries for the same user. */
    @Column(name = "content_text_hash", length = 64)
    private String contentTextHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UploadedFile() {}

    public UploadedFile(AppUser user, StoredUploadResult result, Instant now) {
        this(user, result, now, null);
    }

    public UploadedFile(AppUser user, StoredUploadResult result, Instant now, String contentTextHash) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user");
        this.storedFileName = Objects.requireNonNull(result.storedFileName(), "storedFileName");
        this.originalFileName = Objects.requireNonNull(result.originalFileName(), "originalFileName");
        this.sizeBytes = result.sizeBytes();
        this.relativePath = Objects.requireNonNull(result.relativePath(), "relativePath");
        this.pdf = result.isPdf();
        this.extractedTextTruncated = result.extractedTextTruncated();
        this.contentTextHash = contentTextHash;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getContentTextHash() {
        return contentTextHash;
    }

    public String getStoredFileName() {
        return storedFileName;
    }
}
