package com.typeforsheet.backend.domain.record.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecordDetailResponse(
    Long recordId,
    Long authorId,
    Music music,
    @Schema(nullable = true) BigDecimal rating,
    @Schema(nullable = true) String content,
    boolean isPrivate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean isOwner) {
  public record Music(
      Long musicId,
      MusicType type,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String artistName,
      @Schema(nullable = true) String coverImageUrl) {}
}
