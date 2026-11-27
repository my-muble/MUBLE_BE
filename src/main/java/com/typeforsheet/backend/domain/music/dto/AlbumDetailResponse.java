package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AlbumDetailResponse(
    @Schema(
            description = "MUBLE 내부 앨범 ID",
            example = "123",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long albumId,
    @Schema(
            description = "MusicBrainz Release Group 참조",
            implementation = MusicReferenceSchema.class,
            requiredMode = Schema.RequiredMode.REQUIRED)
        ExternalReference reference,
    @Schema(description = "앨범 제목", example = "Palette", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
    @Schema(description = "아티스트 크레딧 이름", example = "IU", nullable = true) String artistName,
    @Schema(
            description = "첫 번째 아티스트의 MusicBrainz Artist MBID",
            example = "b1234567-1234-1234-1234-123456789abc",
            nullable = true)
        String artistId,
    @Schema(description = "최초 발매일. 연도 또는 연월만 제공될 수 있음", example = "2017-04-21", nullable = true)
        String releaseDate,
    @Schema(description = "Release Group 기본 유형", example = "Album", nullable = true)
        String releaseType,
    @Schema(
            description = "커버 URL. 비활성·없음·조회 실패 시 null",
            example =
                "https://coverartarchive.org/release-group/a1234567-1234-1234-1234-123456789abc/front-250",
            nullable = true)
        String coverImageUrl,
    @Schema(
            description = "대표 Release의 수록곡 목록. Release가 없으면 빈 배열",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<AlbumTrack> tracks,
    @Schema(
            description =
                "대표 Release의 모든 디스크 수록곡 항목 수. 중복 Recording도 각각 계산하며, 빈 목록은 0, 정보 불완전 시 null",
            types = {"integer", "null"},
            format = "int32",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Integer trackCount,
    @Schema(
            description =
                "대표 Release의 총 재생시간(ms). 빈 목록, 정보 불완전, 재생시간 누락·음수 또는 Long 합계 초과 시 null. 0은 유효",
            types = {"integer", "null"},
            format = "int64",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long totalDurationMs) {
  public record AlbumTrack(
      @Schema(description = "매체 번호", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
          int discNumber,
      @Schema(description = "트랙 표기 번호", example = "A1", nullable = true) String trackNumber,
      @Schema(description = "곡 제목", example = "Palette", nullable = true) String title,
      @Schema(description = "트랙 아티스트 이름", example = "IU", nullable = true) String artistName,
      @Schema(description = "재생시간(밀리초)", example = "217000", nullable = true) Long durationMs,
      @Schema(
              description = "MusicBrainz Recording MBID. MUBLE 내부 곡 ID가 아님",
              example = "c1234567-1234-1234-1234-123456789abc",
              nullable = true)
          String recordingId) {}
}
