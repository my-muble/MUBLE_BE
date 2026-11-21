package com.typeforsheet.backend.domain.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record CreateRecordRequest(
    @Schema(
            description = "MUBLE 내부 음악 ID",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long musicId,
    @Schema(description = "0.5부터 5.0까지 0.5점 단위. 미선택 시 null", example = "4.5", nullable = true)
        BigDecimal rating,
    @Schema(description = "감상 본문. 최대 500자", nullable = true) String content,
    @Schema(description = "나만 보기 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean isPrivate) {}
