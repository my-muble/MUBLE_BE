package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public final class RecordSuccessSchemas {
  private RecordSuccessSchemas() {}

  @Schema(name = "RecordWriteSuccess", description = "기록 작성·수정 성공 응답")
  public record Write(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean success,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUCCESS") String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CreateRecordResponse data) {}

  @Schema(name = "RecordDetailSuccess", description = "기록 상세 성공 응답")
  public record Detail(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean success,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUCCESS") String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RecordDetailResponse data) {}
}
