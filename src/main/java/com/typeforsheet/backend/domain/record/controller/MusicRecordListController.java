package com.typeforsheet.backend.domain.record.controller;

import com.typeforsheet.backend.domain.record.dto.MusicRecordListResponse;
import com.typeforsheet.backend.domain.record.dto.MusicRecordListSuccessSchemas;
import com.typeforsheet.backend.domain.record.dto.RecordErrorResponse;
import com.typeforsheet.backend.domain.record.service.MusicRecordListService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music/{musicId}/records")
@RequiredArgsConstructor
@Tag(name = "음악 기록", description = "음악 감상 기록 API")
@SecurityRequirement(name = "bearerAuth")
public class MusicRecordListController {
  private final MusicRecordListService service;

  @GetMapping("/me")
  @Operation(
      summary = "음악별 나의 기록 목록 조회",
      description =
          """
      **조회 대상**
      선택한 앨범 또는 곡에 대해 로그인한 사용자가 작성한 모든 유효한 공개·비공개 기록을 조회하고 집계합니다.
      별점만, 본문만, 별점과 본문을 모두 작성한 기록이 포함됩니다.

      **정렬 기준**
      최초 작성일(createdAt) 내림차순, 동일 시 기록 ID 내림차순입니다. 수정해도 최초 작성일과 정렬 순서는 유지됩니다.

      **조회 방식**
      미리보기: GET /api/music/{musicId}/records/me?limit=3. 전체보기: 같은 API의 커서 페이지네이션.
      """
              + COMMON_DESCRIPTION)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공. 기록이 없으면 200과 빈 목록 반환",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MusicRecordListSuccessSchemas.Mine.class),
                examples = {
                  @ExampleObject(
                      name = "첫 페이지 (limit=3)",
                      value = MusicRecordListApiExamples.MINE_FIRST),
                  @ExampleObject(
                      name = "마지막 페이지 (limit=3)",
                      value = MusicRecordListApiExamples.MINE_LAST),
                  @ExampleObject(name = "기록 없음", value = MusicRecordListApiExamples.MINE_EMPTY),
                  @ExampleObject(
                      name = "커서 이후 결과 없음",
                      value = MusicRecordListApiExamples.MINE_AFTER_CURSOR)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = INVALID_DESCRIPTION,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 토큰 누락 또는 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "USER_NOT_FOUND: 사용자 없음; MUSIC_ITEM_NOT_FOUND: 내부 음악 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER),
                  @ExampleObject(name = "MUSIC_ITEM_NOT_FOUND", value = RecordApiExamples.MUSIC)
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
  public ApiResponse<MusicRecordListResponse<MusicRecordListResponse.Mine>> getMine(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "양의 MUBLE 내부 음악 ID. 외부 MusicBrainz MBID가 아님",
              required = true,
              schema = @Schema(type = "integer", format = "int64", minimum = "1"))
          @PathVariable
          String musicId,
      @Parameter(
              description = "한 번의 요청에서 조회할 기록 수. 기본값 20개, 허용 범위 1~50개.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      defaultValue = "20",
                      minimum = "1",
                      maximum = "50"))
          @RequestParam(required = false)
          String limit,
      @Parameter(
              description = CURSOR_DATE_DESCRIPTION,
              schema = @Schema(type = "string", format = "local-date-time"))
          @RequestParam(required = false)
          String cursorCreatedAt,
      @Parameter(
              description = "이전 응답 nextCursor.recordId. cursorCreatedAt과 함께 전달하는 양의 정수",
              schema = @Schema(type = "integer", format = "int64", minimum = "1"))
          @RequestParam(required = false)
          String cursorId) {
    return ApiResponse.success(
        "나의 음악 기록 목록 조회에 성공했습니다.",
        service.getMine(
            Long.valueOf(jwt.getSubject()),
            number(musicId),
            limit(limit),
            date(cursorCreatedAt),
            optionalId(cursorId)));
  }

  @GetMapping("/others")
  @Operation(
      summary = "음악별 타인의 기록 목록 조회",
      description =
          """
      **조회 대상**
      선택한 앨범 또는 곡에 대해 로그인한 사용자를 제외한 다른 사용자의 공개 기록을 조회합니다.
      유효한 본문이 있는 공개 기록만 조회·집계합니다. 별점만 있거나 본문이 없거나 공백뿐인 기록과 비공개 기록은 제외하고 별점 없이 본문만 있는 기록은 포함합니다.

      **정렬 기준**
      최초 작성일(createdAt) 내림차순, 동일 시 기록 ID 내림차순입니다. 수정해도 최초 작성일과 정렬 순서는 유지됩니다.

      **조회 방식**
      미리보기: GET /api/music/{musicId}/records/others?limit=10. 전체보기: 같은 API의 커서 페이지네이션.
      """
              + COMMON_DESCRIPTION)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공. 기록이 없으면 200과 빈 목록 반환",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MusicRecordListSuccessSchemas.Other.class),
                examples = {
                  @ExampleObject(
                      name = "첫 페이지 (limit=2)",
                      value = MusicRecordListApiExamples.OTHERS_FIRST),
                  @ExampleObject(
                      name = "마지막 페이지 (limit=2)",
                      value = MusicRecordListApiExamples.OTHERS_LAST),
                  @ExampleObject(name = "기록 없음", value = MusicRecordListApiExamples.OTHERS_EMPTY),
                  @ExampleObject(
                      name = "커서 이후 결과 없음",
                      value = MusicRecordListApiExamples.OTHERS_AFTER_CURSOR)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = INVALID_DESCRIPTION,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN: 토큰 누락 또는 인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = @ExampleObject(value = RecordApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "USER_NOT_FOUND: 사용자 없음; MUSIC_ITEM_NOT_FOUND: 내부 음악 없음",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecordErrorResponse.class),
                examples = {
                  @ExampleObject(name = "USER_NOT_FOUND", value = RecordApiExamples.USER),
                  @ExampleObject(name = "MUSIC_ITEM_NOT_FOUND", value = RecordApiExamples.MUSIC)
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
  public ApiResponse<MusicRecordListResponse<MusicRecordListResponse.Other>> getOthers(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "양의 MUBLE 내부 음악 ID. 외부 MusicBrainz MBID가 아님",
              required = true,
              schema = @Schema(type = "integer", format = "int64", minimum = "1"))
          @PathVariable
          String musicId,
      @Parameter(
              description = "한 번의 요청에서 조회할 기록 수. 기본값 20개, 허용 범위 1~50개.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      defaultValue = "20",
                      minimum = "1",
                      maximum = "50"))
          @RequestParam(required = false)
          String limit,
      @Parameter(
              description = CURSOR_DATE_DESCRIPTION,
              schema = @Schema(type = "string", format = "local-date-time"))
          @RequestParam(required = false)
          String cursorCreatedAt,
      @Parameter(
              description = "이전 응답 nextCursor.recordId. cursorCreatedAt과 함께 전달하는 양의 정수",
              schema = @Schema(type = "integer", format = "int64", minimum = "1"))
          @RequestParam(required = false)
          String cursorId) {
    return ApiResponse.success(
        "타인의 음악 기록 목록 조회에 성공했습니다.",
        service.getOthers(
            Long.valueOf(jwt.getSubject()),
            number(musicId),
            limit(limit),
            date(cursorCreatedAt),
            optionalId(cursorId)));
  }

  private static final String CURSOR_DATE_DESCRIPTION =
      "이전 응답 nextCursor.createdAt. 첫 페이지에는 생략하고 다음 페이지에는 cursorId와 함께 원본 그대로 전달합니다. 시간대 오프셋 없는 ISO 로컬 날짜·시간(예: 2026-11-21T18:00:00.123456); 초는 생략 가능하며 소수 초는 초 뒤에 1~9자리입니다. 반올림하거나 절삭하지 마세요.";
  private static final String INVALID_DESCRIPTION =
      "INVALID_REQUEST: 음악 ID가 양의 정수가 아니거나, limit이 정수가 아니거나 1~50 범위를 벗어남; 커서 한쪽만 전달하거나 날짜 형식 또는 양의 cursorId가 잘못됨. 빈 문자열과 숫자 범위 초과도 포함";
  private static final String COMMON_DESCRIPTION =
      """

      첫 페이지에는 두 커서를 모두 생략하고, 다음 페이지에는 nextCursor의 두 값을 함께 원본 그대로 전달합니다.
      예: ?limit=3&cursorCreatedAt=2026-11-21T18%3A00%3A00.123456&cursorId=103 (날짜는 URL 인코딩).

      **응답 및 유의사항**
      musicId는 외부 MBID가 아닌 서비스 내부 ID입니다. 앨범과 곡은 서로 다른 내부 ID를 사용하며 앨범에 수록곡 기록을 합산하지 않습니다.
      cursorCreatedAt은 시간대 오프셋이 없는 ISO 로컬 날짜·시간으로 초 및 소수 초를 포함할 수 있습니다. 반환된 마이크로초를 반올림하거나 절삭하지 마세요.
      전체 수는 results.size가 아닌 totalRecordCount를 사용하며 limit이나 커서의 영향을 받지 않습니다. 커서 이후 결과가 없어도 전체 수는 0이 아닐 수 있습니다.
      조회 결과가 없으면 results=[]이고, 해당 음악에 조회 가능한 기록이 없으면 totalRecordCount=0입니다. hasNext=false이면 nextCursor=null이며 추가 조회를 종료합니다.
      기록 작성·수정·삭제 후 목록을 재조회하세요. 공개 범위나 본문 변경은 타인 목록과 개수에 영향을 줍니다. 최신 기록 확인은 커서 없이 첫 페이지부터 다시 시작합니다.
      페이지 사이 데이터가 변경되면 전체 결과의 고정된 스냅샷은 보장하지 않습니다.

      **프론트엔드 처리 사항**
      100+ 표기, 날짜 표시, 본문 말줄임, 기본 프로필 이미지와 null 별점의 표시 생략은 프론트엔드에서 처리합니다.
      """;

  private long number(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private Long optionalId(String value) {
    return value == null ? null : number(value);
  }

  private int limit(String value) {
    if (value == null) return 20;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private LocalDateTime date(String value) {
    if (value == null) return null;
    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }
}
