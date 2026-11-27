CREATE TABLE archive_track_albums (
    track_id BIGINT NOT NULL PRIMARY KEY,
    release_group_id VARCHAR(255) NULL,
    CONSTRAINT fk_archive_track_albums_track
        FOREIGN KEY (track_id) REFERENCES music_items(id) ON DELETE CASCADE
);
