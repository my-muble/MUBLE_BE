package com.typeforsheet.backend.domain.record.controller;

import com.typeforsheet.backend.domain.record.dto.*;
import com.typeforsheet.backend.domain.record.service.RecordCreateService;
import com.typeforsheet.backend.domain.record.service.RecordDetailService;
import com.typeforsheet.backend.domain.record.service.RecordMutationService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
      별점은 null 또는 0.5~5.0의 0.5점 단위입니다. 본문은 최대 500자이며 공백만 있으면 미입력입니다.
      별점과 본문에 null을 전달하면 값을 제거하고, isPrivate에 null을 전달하면 거부합니다.
      최종적으로 별점 또는 본문 중 하나가 필요하며 음악과 작성자는 변경할 수 없습니다.
      실제 값이 바뀐 경우에만 수정일을 갱신하고 최초 작성일은 유지합니다.
      """)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = UpdateRecordRequest.class),
              examples = @ExampleObject(value = RecordApiExamples.REQUEST_PATCH)))
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 수정 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordSuccessSchemas.Write.class),
                examples = @ExampleObject(value = RecordApiExamples.UPDATED))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 잘못된 수정 요청",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED: 타인의 공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.DENIED))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "RECORD_NOT_FOUND: 기록 없음 또는 타인의 비공개 기록; USER_NOT_FOUND: 사용자 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "RECORD_NOT_FOUND", value = RecordApiExamples.RECORD),
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR: 서버 또는 DB 오류",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INTERNAL)))
  })
  public ApiResponse<CreateRecordResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "양의 기록 ID",
              schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "1"))
          @PathVariable
          String recordId,
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
        description = "기록 삭제 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordDeleteResponseSchema.class),
                examples = @ExampleObject(value = RecordApiExamples.DELETE))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 잘못된 기록 ID",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED: 타인의 공개 기록",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.DENIED))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "RECORD_NOT_FOUND: 기록 없음 또는 타인의 비공개 기록; USER_NOT_FOUND: 사용자 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "RECORD_NOT_FOUND", value = RecordApiExamples.RECORD),
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR: 서버 또는 DB 오류",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INTERNAL)))
  })
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "양의 기록 ID",
              schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "1"))
          @PathVariable
          String recordId) {
    mutationService.delete(Long.valueOf(jwt.getSubject()), parseId(recordId));
    return ApiResponse.success("음악 기록 삭제에 성공했습니다.");
  }

  @GetMapping("/{recordId}")
  @Operation(
      summary = "음악 기록 상세 조회",
      description =
          """
      유효한 Access Token이 필요합니다. 공개 기록은 로그인한 사용자에게 공개되며 비공개 기록은 작성자만 조회할 수 있습니다.
      작성자 닉네임은 조회자가 아닌 기록 작성자의 값이며 없으면 null입니다. 프로필 이미지 URL은 현재 항상 null입니다.
      타인 목록에서는 별점 전용 기록을 제외하지만 공개 별점 전용 기록도 ID로 직접 상세 조회할 수 있습니다. 타인의 비공개·삭제된·없는 기록은 404입니다.
      외부 음악 조회 장애 시 음악 ID와 타입 및 기록은 유지하고 제목, 아티스트, 커버는 null로 반환합니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 조회 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordSuccessSchemas.Detail.class),
                examples = {
                  @ExampleObject(name = "본인 공개", value = RecordApiExamples.DETAIL_OWNER_PUBLIC),
                  @ExampleObject(name = "본인 비공개", value = RecordApiExamples.DETAIL_OWNER_PRIVATE),
                  @ExampleObject(name = "타인 별점 전용", value = RecordApiExamples.DETAIL_OTHER_RATING),
                  @ExampleObject(
                      name = "타인 본문 전용 닉네임 없음",
                      value = RecordApiExamples.DETAIL_OTHER_TEXT),
                  @ExampleObject(name = "외부 메타데이터 없음", value = RecordApiExamples.DETAIL)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 잘못된 기록 ID",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description =
            "RECORD_NOT_FOUND: 기록 없음 또는 타인의 비공개 기록; USER_NOT_FOUND: 사용자 없음; 예외적으로 ALBUM_NOT_FOUND 또는 TRACK_NOT_FOUND",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "RECORD_NOT_FOUND", value = RecordApiExamples.RECORD),
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "MUSIC_MAPPING_MISSING: 내부 음악 매핑 누락; INTERNAL_SERVER_ERROR: 서버 또는 DB 오류",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "MUSIC_MAPPING_MISSING", value = RecordApiExamples.MAPPING),
                  @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = RecordApiExamples.INTERNAL)
                }))
  })
  public ApiResponse<RecordDetailResponse> getDetail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "양의 기록 ID",
              schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "1"))
          @PathVariable
          String recordId) {
    return ApiResponse.success(
        "음악 기록 조회에 성공했습니다.",
        detailService.getDetail(Long.valueOf(jwt.getSubject()), parseId(recordId)));
  }

  @PostMapping
  @Operation(
      summary = "음악 기록 작성",
      description =
          """
      앨범 또는 곡의 MUBLE 내부 ID로 기록합니다. 별점은 null 또는 0.5~5.0의 0.5점 단위입니다.
      본문은 최대 500자이며 공백만 있으면 미입력입니다. 별점 또는 본문 중 하나 이상이 필요합니다.
      새 작성마다 새 Idempotency-Key를 사용하고 재시도에는 같은 키를 사용합니다. 키는 사용자별 최대 128자입니다.
      요청을 먼저 검증하므로 유효하지 않은 요청은 기존 키여도 400입니다. 비교는 정규화된 값 기준이며 4.5와 4.50은 동일합니다.
      생성 후 30일 동안 같은 키와 같은 요청은 최초 응답을 반환합니다. 같은 키와 다른 유효한 요청 또는 삭제 후 같은 요청은 409입니다.
      30일 후 멱등성이 보장되지 않으므로 클라이언트는 키를 재사용하지 않아야 합니다.
      """)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = CreateRecordRequest.class),
              examples = @ExampleObject(value = RecordApiExamples.REQUEST_CREATE)))
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "작성 또는 동일 요청 재시도 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordSuccessSchemas.Write.class),
                examples = @ExampleObject(value = RecordApiExamples.CREATED))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 잘못된 키 또는 요청",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "USER_NOT_FOUND: 사용자 없음; MUSIC_ITEM_NOT_FOUND: 음악 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER),
                  @ExampleObject(name = "MUSIC_ITEM_NOT_FOUND", value = RecordApiExamples.MUSIC)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "IDEMPOTENCY_KEY_CONFLICT: 다른 요청에 키 사용; RECORD_ALREADY_DELETED: 기록 삭제 후 재시도",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "IDEMPOTENCY_KEY_CONFLICT",
                      value = RecordApiExamples.CONFLICT),
                  @ExampleObject(name = "RECORD_ALREADY_DELETED", value = RecordApiExamples.DELETED)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR: 서버 또는 DB 오류",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INTERNAL))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "503",
        description = "RECORD_REQUEST_RETRY: 잠금 시간 초과. 재시도 가능",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.RETRY)))
  })
  public ApiResponse<CreateRecordResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "작성 동작별 고유 키. 공백만 있거나 제어 문자를 포함할 수 없으며 최대 128자", required = true)
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
