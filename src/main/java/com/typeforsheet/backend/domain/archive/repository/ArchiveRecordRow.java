package com.typeforsheet.backend.domain.archive.repository;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import java.time.LocalDateTime;

public record ArchiveRecordRow(
    Long recordId, Long musicItemId, MusicType musicType, LocalDateTime createdAt) {}
