package com.typeforsheet.backend.domain.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MusicExternalReference", description = "MusicBrainz 외부 참조. MUBLE 내부 ID가 아닙니다.")
public record MusicReferenceSchema(
    @Schema(
            description = "외부 서비스",
            example = "MUSICBRAINZ",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String provider,
    @Schema(
            description = "외부 ID 종류: RELEASE_GROUP 또는 RECORDING",
            example = "RELEASE_GROUP",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String idType,
    @Schema(
            description = "MusicBrainz MBID",
            example = "a1234567-1234-1234-1234-123456789abc",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id) {}
