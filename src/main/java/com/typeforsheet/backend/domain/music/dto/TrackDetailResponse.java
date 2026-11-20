package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrackDetailResponse(
    @Schema(
            description = "MUBLE 내부 곡 ID",
            example = "456",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long trackId,
    @Schema(
            description = "MusicBrainz Recording 참조",
            implementation = MusicReferenceSchema.class,
            requiredMode = Schema.RequiredMode.REQUIRED)
        ExternalReference reference,
    @Schema(
            description = "Recording 제목",
            example = "밤편지",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
    @Schema(description = "아티스트 크레딧 이름", example = "IU", nullable = true) String artistName,
    @Schema(
            description = "첫 번째 아티스트의 MusicBrainz Artist MBID",
            example = "b1234567-1234-1234-1234-123456789abc",
            nullable = true)
        String artistId,
    @Schema(description = "Recording 재생시간(밀리초)", example = "253000", nullable = true)
        Long durationMs,
    @Schema(description = "선정된 대표 Release의 제목", example = "Palette", nullable = true)
        String albumTitle,
    @Schema(
            description = "대표 앨범의 MusicBrainz Release Group MBID",
            example = "a1234567-1234-1234-1234-123456789abc",
            nullable = true)
        String releaseGroupId,
    @Schema(description = "커버 URL. 앨범이 없거나 비활성·없음·조회 실패 시 null", nullable = true)
        String coverImageUrl) {}
