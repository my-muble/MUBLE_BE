package com.typeforsheet.backend.domain.archive.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ArchiveTrackAlbumRepository {
  private final JdbcTemplate jdbc;

  public Optional<TrackAlbum> find(Long trackId) {
    return jdbc
        .query(
            "SELECT release_group_id FROM archive_track_albums WHERE track_id = ?",
            (rs, row) -> new TrackAlbum(rs.getString(1)),
            trackId)
        .stream()
        .findFirst();
  }

  public TrackAlbum remember(Long trackId, String releaseGroupId) {
    jdbc.update(
        """
        INSERT INTO archive_track_albums (track_id, release_group_id) VALUES (?, ?)
        ON DUPLICATE KEY UPDATE track_id = track_id
        """,
        trackId,
        releaseGroupId);
    // A locking read returns the winning row even under MySQL's repeatable-read isolation.
    return jdbc.queryForObject(
        "SELECT release_group_id FROM archive_track_albums WHERE track_id = ? FOR UPDATE",
        (rs, row) -> new TrackAlbum(rs.getString(1)),
        trackId);
  }

  // A row with a null Release Group means the catalog confirmed the track has no album.
  public record TrackAlbum(String releaseGroupId) {}
}
