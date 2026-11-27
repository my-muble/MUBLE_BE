package com.typeforsheet.backend.domain.archive.repository;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import java.time.LocalDateTime;

public record ArchiveRecordRow(
    Long recordId,
    Long musicItemId,
    MusicType musicType,
    LocalDateTime createdAt,
    Byte ratingHalfSteps,
    String content) {

  public ArchiveRecordRow(
      Long recordId, Long musicItemId, MusicType musicType, LocalDateTime createdAt) {
    this(recordId, musicItemId, musicType, createdAt, null, null);
  }
}
