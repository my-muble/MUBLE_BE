package com.typeforsheet.backend.domain.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ArchiveDayTrackResponse(
    LocalDate date,
    String albumKey,
    Track track,
    long recordCount,
    @Schema(description = "선택 날짜에 작성한 곡 기록, 최신순") List<TrackRecord> records) {

  public record Track(
      Long trackId,
      @Schema(nullable = true) Integer discNumber,
      @Schema(nullable = true) String trackNumber,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String artistName,
      @Schema(nullable = true) String coverImageUrl) {}

  public record TrackRecord(
      Long recordId,
      @Schema(description = "한국 시간 기준 작성 시각") OffsetDateTime createdAt,
      @Schema(nullable = true) BigDecimal rating,
      @Schema(nullable = true) String content) {}
}
