package com.typeforsheet.backend.domain.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ArchiveDayAlbumResponse(
    LocalDate date,
    Album album,
    @Schema(description = "앨범 자체 기록과 수록곡 기록의 합계") long totalRecordCount,
    long albumRecordCount,
    long trackRecordCount,
    @Schema(description = "앨범 자체 기록, 작성 시각 최신순") List<AlbumRecord> albumRecords,
    @Schema(description = "앨범 수록곡 순서. 기록이 없는 수록곡도 포함") List<Track> tracks) {

  public record Album(
      String albumKey,
      @Schema(nullable = true) Long albumId,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String artistName,
      @Schema(nullable = true) String coverImageUrl) {}

  public record AlbumRecord(
      Long recordId,
      @Schema(description = "한국 시간 기준 작성 시각") OffsetDateTime createdAt,
      @Schema(nullable = true) BigDecimal rating,
      @Schema(nullable = true) String content) {}

  public record Track(
      @Schema(description = "수록곡 내부 ID. 기록이 없으면 null", nullable = true) Long trackId,
      @Schema(description = "MusicBrainz Recording ID", nullable = true) String recordingId,
      @Schema(nullable = true) Integer discNumber,
      @Schema(nullable = true) String trackNumber,
      @Schema(nullable = true) String title,
      long recordCount) {}
}
