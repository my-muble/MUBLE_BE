package com.typeforsheet.backend.domain.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ArchiveDayResponse(
    @Schema(description = "선택한 작성일(한국 시간)", example = "2026-09-25") LocalDate date,
    @Schema(description = "선택 날짜의 앨범·곡 기록 합계", example = "12") long recordCount,
    @Schema(description = "선택 날짜의 고유 앨범 그룹 수", example = "8") int albumCount,
    @Schema(description = "그룹 내 최신 기록 작성 시각 기준 내림차순") List<AlbumSummary> albums) {

  public record AlbumSummary(
      @Schema(description = "시간순 카드와 동일한 앨범 그룹 키") String albumKey,
      @Schema(description = "매핑이 존재하는 경우 MUBLE 내부 앨범 ID", nullable = true) Long albumId,
      @Schema(description = "앨범 제목", nullable = true) String title,
      @Schema(description = "앨범 아티스트", nullable = true) String artistName,
      @Schema(description = "앨범 커버 URL", nullable = true) String coverImageUrl,
      @Schema(description = "해당 날짜의 그룹 내 앨범·곡 기록 합계", example = "2") long recordCount,
      @Schema(description = "별점이 있는 기록만 평균(소수점 한 자리)", nullable = true) BigDecimal averageRating,
      @Schema(description = "가장 최근에 작성된 본문. 줄임표는 클라이언트에서 처리", nullable = true)
          String contentPreview) {}
}
