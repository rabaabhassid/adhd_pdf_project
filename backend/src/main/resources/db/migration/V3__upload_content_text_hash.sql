-- Fingerprint of extracted PDF text for the same user: skip redundant LLM calls on re-upload.
ALTER TABLE uploaded_file ADD COLUMN content_text_hash VARCHAR(64) NULL;

CREATE INDEX idx_uploaded_file_user_content_hash ON uploaded_file (user_id, content_text_hash);
