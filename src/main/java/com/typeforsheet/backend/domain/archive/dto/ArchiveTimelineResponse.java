package com.typeforsheet.backend.domain.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "아카이브 시간순 목록")
public record ArchiveTimelineResponse(
    @Schema(description = "조회 연도", example = "2026") int year,
    @Schema(description = "선택 연도와 무관하게 사용자의 기록이 하나라도 있는지 여부", example = "true")
        boolean hasAnyRecords,
    @Schema(description = "기록이 존재하는 월만 최신순으로 반환") List<MonthGroup> months) {

  public record MonthGroup(
      @Schema(description = "연월", example = "2026-09") String yearMonth,
      @Schema(description = "해당 월의 날짜별 카드 목록(최신순)") List<DaySummary> days) {}

  public record DaySummary(
      @Schema(description = "기록 작성일(한국 시간)", example = "2026-09-25") LocalDate date,
      @Schema(description = "해당 날짜에 작성한 앨범·곡 기록 수", example = "8") long recordCount,
      @Schema(description = "해당 날짜 기록에 포함된 고유 앨범 수", example = "12") long albumCount,
      @Schema(description = "최대 5개의 고유 앨범 커버") List<AlbumCover> covers) {}

  public record AlbumCover(
      @Schema(description = "앨범 중복 제거용 식별자", example = "a1234567-1234-1234-1234-123456789abc")
          String albumKey,
      @Schema(description = "앨범 커버 URL. 조회할 수 없으면 null", nullable = true) String coverImageUrl) {}
}
