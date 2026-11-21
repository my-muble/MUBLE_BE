package com.typeforsheet.backend.domain.record.controller;

import com.typeforsheet.backend.domain.record.dto.CreateRecordRequest;
import com.typeforsheet.backend.domain.record.dto.CreateRecordResponse;
import com.typeforsheet.backend.domain.record.service.RecordCreateService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name = "음악 기록", description = "음악 감상 기록 API")
@SecurityRequirement(name = "bearerAuth")
public class RecordController {
  private final RecordCreateService service;

  @PostMapping
  @Operation(
      summary = "음악 기록 작성",
      description =
          """
      앨범 또는 곡의 MUBLE 내부 ID로 기록합니다. 별점 또는 본문 중 하나가 필요합니다.
      새로운 작성마다 새 Idempotency-Key를 사용하고 재시도에는 같은 키를 사용합니다.
      이력은 생성 후 30일 동안 보장되며, 이후 클라이언트는 같은 키를 재사용하지 않아야 합니다.
      같은 키와 같은 요청은 최초 응답을 반환하며, 기록 삭제 후 재시도 또는 다른 요청은 409입니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "작성 또는 동일 요청 재시도 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 키 또는 요청"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "사용자 또는 음악이 없음"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "키 재사용 충돌 또는 삭제된 기록"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "503",
        description = "잠금 시간 초과. 재시도 가능")
  })
  public ApiResponse<CreateRecordResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "작성 동작별 고유 키. 최대 128자", required = true)
          @RequestHeader(value = "Idempotency-Key", required = false)
          String key,
      @RequestBody CreateRecordRequest request) {
    return ApiResponse.success(
        "음악 기록 작성에 성공했습니다.", service.create(Long.valueOf(jwt.getSubject()), key, request));
  }
}
