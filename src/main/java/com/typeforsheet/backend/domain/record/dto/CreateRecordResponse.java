package com.typeforsheet.backend.domain.record.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateRecordResponse(
    Long recordId,
    Long musicId,
    MusicType musicType,
    BigDecimal rating,
    String content,
    boolean isPrivate,
    LocalDateTime createdAt,
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
