package com.typeforsheet.backend.domain.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MusicSuccessSchemas {
  private MusicSuccessSchemas() {}

  @Schema(name = "MusicResolveSuccess")
  public record Resolve(
      @Schema(example = "true") boolean success,
      @Schema(example = "SUCCESS") String code,
      @Schema(example = "음악 ID 조회에 성공했습니다.") String message,
      ResolveMusicResponse data) {}

  @Schema(name = "AlbumDetailSuccess")
  public record Album(
      @Schema(example = "true") boolean success,
      @Schema(example = "SUCCESS") String code,
      @Schema(example = "앨범 상세 조회에 성공했습니다.") String message,
      AlbumDetailResponse data) {}

  @Schema(name = "TrackDetailSuccess")
  public record Track(
      @Schema(example = "true") boolean success,
      @Schema(example = "SUCCESS") String code,
      @Schema(example = "곡 상세 조회에 성공했습니다.") String message,
      TrackDetailResponse data) {}
}
