package com.typeforsheet.backend.domain.home.dto.response;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "홈 타인 공개 기록 카드")
public record HomePublicRecordResponse(
    @Schema(description = "기록 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long recordId,
    @Schema(
            description = "작성자 닉네임. 미설정 시 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorNickname,
    @Schema(description = "기록 대상 음악", requiredMode = Schema.RequiredMode.REQUIRED) Music music,
    @Schema(
            description = "실제 별점. 미입력 시 null",
            types = {"number", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal rating,
    @Schema(
            description = "기록 본문. 임의로 자르지 않으며 미입력 시 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String contentPreview,
    @Schema(
            description = "최초 작성일. 시간대 오프셋 없는 로컬 날짜·시간",
            type = "string",
            format = "local-date-time",
            example = "2026-11-25T10:30:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt) {

  @Schema(description = "공개 기록 대상 음악 정보")
  public record Music(
      @Schema(description = "MUBLE 내부 음악 ID", requiredMode = Schema.RequiredMode.REQUIRED)
          Long musicId,
      @Schema(description = "음악 유형", requiredMode = Schema.RequiredMode.REQUIRED) MusicType type,
      @Schema(
              description = "음악 제목. 외부 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String title,
      @Schema(
              description = "아티스트. 정보가 없거나 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String artistName,
      @Schema(
              description = "커버 이미지 URL. 없거나 조회 실패 시 null",
              types = {"string", "null"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String coverImageUrl) {}
}
