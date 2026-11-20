package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResolveMusicResponse(
    @Schema(
            description = "MUBLE 내부 음악 ID",
            example = "123",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long musicId,
    @Schema(
            description = "내부 음악 유형",
            example = "ALBUM",
            requiredMode = Schema.RequiredMode.REQUIRED)
        MusicType type) {}
