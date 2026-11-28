package com.typeforsheet.backend.domain.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "아카이브 앨범순 목록")
public record ArchiveAlbumListResponse(
    @Schema(description = "기록이 하나 이상 있는 고유 앨범 수", example = "12") int albumCount,
    @Schema(description = "선택한 정렬 기준에 따른 앨범 카드 목록") List<AlbumCard> albums) {

  public record AlbumCard(
      @Schema(
              description = "앨범 중복 제거 및 상세 이동에 사용하는 식별자",
              example = "a1234567-1234-1234-1234-123456789abc")
          String albumKey,
      @Schema(description = "MUBLE 내부 앨범 ID. 수록곡 기록만 있으면 null", nullable = true) Long albumId,
      @Schema(description = "앨범명. 메타데이터를 조회할 수 없으면 null", nullable = true) String title,
      @Schema(description = "대표 아티스트명. 메타데이터를 조회할 수 없으면 null", nullable = true) String artistName,
      @Schema(description = "최신 앨범 커버 URL. 없으면 null", nullable = true) String coverImageUrl,
      @Schema(description = "앨범 자체 및 수록곡 기록의 누적 합계", example = "22") long recordCount) {}
}
