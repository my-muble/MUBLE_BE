package com.typeforsheet.backend.domain.record.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MusicRecordListResponse<T>(
    List<T> results, long totalRecordCount, boolean hasNext, Cursor nextCursor) {
  public record Cursor(LocalDateTime createdAt, Long recordId) {}

  public record Mine(
      Long recordId,
      BigDecimal rating,
      String content,
      boolean isPrivate,
      LocalDateTime createdAt) {}

  public record Other(
      Long recordId,
      Long authorId,
      String authorNickname,
      String authorProfileImageUrl,
      BigDecimal rating,
      String content,
      LocalDateTime createdAt) {}
}
