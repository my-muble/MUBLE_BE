CREATE TABLE music_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    music_item_id BIGINT NOT NULL,
    rating_half_steps TINYINT NULL,
    content VARCHAR(500) NULL,
    is_private BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_music_records_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_music_records_music_item FOREIGN KEY (music_item_id) REFERENCES music_items(id),
    CONSTRAINT chk_music_records_rating CHECK (rating_half_steps IS NULL OR rating_half_steps BETWEEN 1 AND 10),
    CONSTRAINT chk_music_records_content CHECK (rating_half_steps IS NOT NULL OR content IS NOT NULL),
    INDEX idx_music_records_user (user_id),
    INDEX idx_music_records_music_item (music_item_id)
);

CREATE TABLE record_create_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key VARBINARY(512) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    record_id BIGINT NULL,
    response_snapshot TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_record_create_requests_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_record_create_requests_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_record_create_requests_record FOREIGN KEY (record_id) REFERENCES music_records(id) ON DELETE SET NULL,
    INDEX idx_record_create_requests_expires_at (expires_at)
);
