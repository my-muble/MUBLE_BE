package com.typeforsheet.backend.domain.record.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateRecordResponse(
    @Schema(description = "기록 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long recordId,
    @Schema(description = "MUBLE 내부 음악 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        Long musicId,
    @Schema(description = "음악 타입", requiredMode = Schema.RequiredMode.REQUIRED) MusicType musicType,
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
        LocalDateTime updatedAt) {
  public static CreateRecordResponse from(MusicRecord record) {
    return new CreateRecordResponse(
        record.getId(),
        record.getMusicItem().getId(),
        record.getMusicItem().getType(),
        record.getRatingHalfSteps() == null
            ? null
            : BigDecimal.valueOf(record.getRatingHalfSteps()).divide(BigDecimal.valueOf(2)),
        record.getContent(),
        record.isPrivate(),
        record.getCreatedAt(),
        record.getUpdatedAt());
  }
}
