package com.typeforsheet.backend.domain.record.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecordDetailResponse(
    @Schema(description = "기록 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long recordId,
    @Schema(description = "작성자 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long authorId,
    @Schema(
            description = "작성자 닉네임. 없으면 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorNickname,
    @Schema(
            description = "작성자 프로필 이미지 URL. 현재는 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorProfileImageUrl,
    @Schema(description = "대상 음악", requiredMode = Schema.RequiredMode.REQUIRED) Music music,
    @Schema(
            description = "별점. 미선택 시 null",
            types = {"number", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal rating,
    @Schema(
            description = "감상 본문. 미입력 시 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
    @Schema(description = "나만 보기 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isPrivate,
    @Schema(
            description = "최초 작성일. 시간대 오프셋 없는 로컬 날짜·시간",
            type = "string",
            format = "local-date-time",
            example = "2026-11-21T18:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,
    @Schema(
            description = "최종 수정일. 시간대 오프셋 없는 로컬 날짜·시간",
            type = "string",
            format = "local-date-time",
            example = "2026-11-21T18:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt,
    @Schema(description = "현재 사용자가 작성자인지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isOwner) {
  public record Music(
      @Schema(description = "MUBLE 내부 음악 ID", requiredMode = Schema.RequiredMode.REQUIRED)
          Long musicId,
      @Schema(description = "음악 타입", requiredMode = Schema.RequiredMode.REQUIRED) MusicType type,
      @Schema(
              description = "제목. 외부 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String title,
      @Schema(
              description = "아티스트. 정보가 없거나 외부 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String artistName,
      @Schema(
              description = "커버 URL. 정보가 없거나 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String coverImageUrl) {}
}
