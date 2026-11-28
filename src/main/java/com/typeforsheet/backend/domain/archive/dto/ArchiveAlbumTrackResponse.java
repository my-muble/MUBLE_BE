package com.typeforsheet.backend.domain.archive.dto;

import com.typeforsheet.backend.domain.archive.dto.ArchiveDayTrackResponse.TrackRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "전체 기간 누적 수록곡 기록")
public record ArchiveAlbumTrackResponse(
    String albumKey,
    Track track,
    long recordCount,
    @Schema(description = "한국 시간 기준 작성 연도별 그룹. 최신순, 0건이면 빈 배열") List<YearGroup> years) {

  public record Track(
      @Schema(description = "연결된 MUBLE 내부 곡 ID", nullable = true) Long trackId,
      @Schema(description = "곡 ID 연결에 사용하는 MusicBrainz Recording ID", nullable = true)
          String recordingId,
      @Schema(nullable = true) Integer discNumber,
      @Schema(nullable = true) String trackNumber,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String artistName,
      @Schema(nullable = true) String coverImageUrl) {}

  public record YearGroup(
      int year, @Schema(description = "최초 작성 시각 최신순") List<TrackRecord> records) {}
}
