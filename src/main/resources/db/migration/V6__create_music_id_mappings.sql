CREATE TABLE music_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_music_items_type CHECK (type IN ('ALBUM', 'TRACK'))
);

CREATE TABLE music_external_ids (
    id BIGINT NOT NULL AUTO_INCREMENT,
    music_item_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    external_id_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_music_external_ids_provider_type_id UNIQUE (provider, external_id_type, external_id),
    CONSTRAINT fk_music_external_ids_music_item FOREIGN KEY (music_item_id) REFERENCES music_items(id),
    INDEX idx_music_external_ids_music_item (music_item_id)
);
