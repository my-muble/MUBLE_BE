package com.typeforsheet.backend.domain.home.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 앨범 랭킹 카드")
public record HomeRankingResponse(
    @Schema(
            description = "MUBLE 내부 앨범 ID. 외부 MusicBrainz ID가 아님",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long albumId,
    @Schema(description = "앨범 제목", requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(
            description = "앨범 아티스트. 정보가 없으면 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String artistName,
    @Schema(
            description = "앨범 커버 URL. 없거나 조회에 실패하면 null",
            types = {"string", "null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String coverImageUrl,
    @Schema(
            description = "최근 1시간 공개 기록 건수 + 별점이 있는 기록 건수",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long rankingScore) {}
