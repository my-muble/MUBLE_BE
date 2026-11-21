package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 오류 응답")
public record RecordErrorResponse(
    @Schema(example = "false") boolean success,
    @Schema(example = "INVALID_REQUEST") String code,
    @Schema(example = "잘못된 요청입니다.") String message,
    @Schema(nullable = true) Void data) {}
