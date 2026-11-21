package com.typeforsheet.backend.domain.record.controller;

import com.typeforsheet.backend.domain.record.dto.CreateRecordRequest;
import com.typeforsheet.backend.domain.record.dto.CreateRecordResponse;
import com.typeforsheet.backend.domain.record.dto.RecordDetailResponse;
import com.typeforsheet.backend.domain.record.dto.RecordErrorResponse;
import com.typeforsheet.backend.domain.record.dto.UpdateRecordRequest;
import com.typeforsheet.backend.domain.record.service.RecordCreateService;
import com.typeforsheet.backend.domain.record.service.RecordDetailService;
import com.typeforsheet.backend.domain.record.service.RecordMutationService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
  private final RecordDetailService detailService;
  private final RecordMutationService mutationService;

  @PatchMapping("/{recordId}")
  @Operation(
      summary = "음악 기록 수정",
      description =
          """
      작성자만 별점, 본문, 공개 범위를 수정할 수 있습니다. 필드 생략은 기존 값을 유지합니다.
      별점과 본문에 null을 전달하면 값을 제거하고, isPrivate에 null을 전달하면 거부합니다.
      최종적으로 별점 또는 본문 중 하나가 필요하며 음악과 작성자는 변경할 수 없습니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 수정 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 수정 요청",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "타인의 공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "기록 없음 또는 타인의 비공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class)))
  })
  public ApiResponse<CreateRecordResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String recordId,
      @RequestBody UpdateRecordRequest request) {
    return ApiResponse.success(
        "음악 기록 수정에 성공했습니다.",
        mutationService.update(Long.valueOf(jwt.getSubject()), parseId(recordId), request));
  }

  @DeleteMapping("/{recordId}")
  @Operation(summary = "음악 기록 삭제", description = "작성자만 삭제할 수 있습니다. 삭제된 기록을 다시 삭제하면 404입니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 삭제 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 기록 ID",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "타인의 공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "기록 없음 또는 타인의 비공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class)))
  })
  public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String recordId) {
    mutationService.delete(Long.valueOf(jwt.getSubject()), parseId(recordId));
    return ApiResponse.success("음악 기록 삭제에 성공했습니다.");
  }

  @GetMapping("/{recordId}")
  @Operation(
      summary = "음악 기록 상세 조회",
      description =
          """
      유효한 Access Token이 필요합니다. 공개 기록은 로그인한 사용자에게 공개되며 비공개 기록은 작성자만 조회할 수 있습니다.
      외부 음악 조회 장애 시 음악 ID와 타입 및 기록은 유지하고 제목, 아티스트, 커버는 null로 반환합니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 조회 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 기록 ID",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "기록 없음 또는 비공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "DB 또는 서버 오류",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class)))
  })
  public ApiResponse<RecordDetailResponse> getDetail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String recordId) {
    return ApiResponse.success(
        "음악 기록 조회에 성공했습니다.",
        detailService.getDetail(Long.valueOf(jwt.getSubject()), parseId(recordId)));
  }

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
        description = "잘못된 키 또는 요청",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "사용자 또는 음악이 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "키 재사용 충돌 또는 삭제된 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "503",
        description = "잠금 시간 초과. 재시도 가능",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class)))
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

  private Long parseId(String recordId) {
    try {
      return Long.valueOf(recordId);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }
}
