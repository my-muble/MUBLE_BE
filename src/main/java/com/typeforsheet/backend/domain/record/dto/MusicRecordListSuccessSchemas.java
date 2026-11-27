package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MusicRecordListSuccessSchemas {
  private MusicRecordListSuccessSchemas() {}

  @Schema(name = "MusicRecordListMineSuccess", description = "나의 기록 목록 성공 응답")
  public record Mine(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          MusicRecordListResponse<MusicRecordListResponse.Mine> data) {}

  @Schema(name = "MusicRecordListOtherSuccess", description = "타인의 기록 목록 성공 응답")
  public record Other(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          MusicRecordListResponse<MusicRecordListResponse.Other> data) {}
}
