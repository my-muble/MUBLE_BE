package com.typeforsheet.backend.domain.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "검색 결과 reference와 동일한 MusicBrainz 외부 ID")
public record ResolveMusicRequest(
    @NotBlank @Schema(
            description = "지원 서비스",
            example = "MUSICBRAINZ",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String provider,
    @NotBlank @Schema(
            description = "RELEASE_GROUP은 ALBUM, RECORDING은 TRACK",
            example = "RELEASE_GROUP",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String idType,
    @NotBlank @Schema(
            description = "MusicBrainz MBID (UUID 형식)",
            example = "a1234567-1234-1234-1234-123456789abc",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id) {}
