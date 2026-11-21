package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기록 삭제 성공 응답")
public record RecordDeleteResponseSchema(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean success,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUCCESS") String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "음악 기록 삭제에 성공했습니다.")
        String message,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            types = {"null"},
            description = "삭제 성공 시 null")
        Object data) {}
