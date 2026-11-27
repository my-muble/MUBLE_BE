package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MusicRecordListResponse<T>(
    @Schema(
            description = "현재 페이지의 기록 목록. 조회 결과가 없으면 빈 배열",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> results,
    @Schema(
            description = "limit과 Cursor를 적용하기 전 조회 조건에 맞는 전체 기록 수. Cursor 이후 결과가 없어도 0이 아닐 수 있음",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long totalRecordCount,
    @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext,
    @Schema(
            description = "다음 페이지 커서. 마지막 페이지에서는 null",
            types = {"object", "null"},
            implementation = Cursor.class,
            requiredMode = Schema.RequiredMode.REQUIRED)
        Cursor nextCursor) {
  @Schema(name = "MusicRecordListCursor", description = "현재 페이지의 마지막 반환 기록을 기준으로 한 커서")
  public record Cursor(
      @Schema(
              description = "마지막 기록의 최초 작성일. 시간대 오프셋 없는 로컬 날짜·시간",
              type = "string",
              format = "local-date-time",
              example = "2026-11-21T18:00:00.123456",
              requiredMode = Schema.RequiredMode.REQUIRED)
          LocalDateTime createdAt,
      @Schema(description = "마지막 기록 ID", requiredMode = Schema.RequiredMode.REQUIRED)
          Long recordId) {}

  @Schema(name = "MusicRecordListMine", description = "나의 음악 기록")
  public record Mine(
      @Schema(description = "기록 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long recordId,
      @Schema(
              description = "별점. 미작성 시 null",
              types = {"number", "null"},
              minimum = "0.5",
              maximum = "5.0",
              multipleOf = 0.5,
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal rating,
      @Schema(
              description = "본문. 미작성 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String content,
      @Schema(
              description = "나만 보기 여부. true는 비공개, false는 공개",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean isPrivate,
      @Schema(
              description = "최초 작성일. 시간대 오프셋 없는 로컬 날짜·시간",
              type = "string",
              format = "local-date-time",
              example = "2026-11-21T18:00:00.123456",
              requiredMode = Schema.RequiredMode.REQUIRED)
          LocalDateTime createdAt) {}

  @Schema(name = "MusicRecordListOther", description = "타인의 공개 음악 기록")
  public record Other(
      @Schema(description = "기록 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long recordId,
      @Schema(description = "작성자 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long authorId,
      @Schema(
              description = "작성자 닉네임. 미설정 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String authorNickname,
      @Schema(
              description = "프로필 이미지 URL. 현재 미구현으로 항상 null; 프론트엔드에서 기본 이미지 표시",
              types = {"null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String authorProfileImageUrl,
      @Schema(
              description = "별점. 미작성 시 null",
              types = {"number", "null"},
              minimum = "0.5",
              maximum = "5.0",
              multipleOf = 0.5,
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal rating,
      @Schema(
              description = "유효한 본문. null 또는 공백만 있는 본문은 제외",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String content,
      @Schema(
              description = "최초 작성일. 시간대 오프셋 없는 로컬 날짜·시간",
              type = "string",
              format = "local-date-time",
              example = "2026-11-21T18:00:00.123456",
              requiredMode = Schema.RequiredMode.REQUIRED)
          LocalDateTime createdAt) {}
}
