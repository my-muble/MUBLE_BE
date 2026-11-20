package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import java.util.List;

public record AlbumDetailResponse(
    Long albumId,
    ExternalReference reference,
    String title,
    String artistName,
    String artistId,
    String releaseDate,
    String releaseType,
    String coverImageUrl,
    List<AlbumTrack> tracks) {
  public record AlbumTrack(
      int discNumber,
      String trackNumber,
      String title,
      String artistName,
      Long durationMs,
      String recordingId) {}
}
