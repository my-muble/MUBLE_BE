package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 오류 응답")
public record RecordErrorResponse(
    @Schema(example = "false") boolean success,
    @Schema(description = "오류 코드", requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(description = "오류 메시지", requiredMode = Schema.RequiredMode.REQUIRED) String message,
    @Schema(
            description = "오류 응답은 항상 null",
            types = {"null"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        Object data) {}
