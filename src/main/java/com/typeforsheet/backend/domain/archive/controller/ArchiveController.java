package com.typeforsheet.backend.domain.archive.controller;

import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse;
import com.typeforsheet.backend.domain.archive.service.ArchiveTimelineService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Year;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
@Tag(name = "아카이브", description = "본인 음악 기록 아카이브 API")
@SecurityRequirement(name = "bearerAuth")
public class ArchiveController {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private final ArchiveTimelineService archiveTimelineService;

  @GetMapping("/timeline")
  @Operation(
      summary = "시간순 아카이브 조회",
      description =
          """
          선택 연도의 본인 앨범·곡 기록을 월과 날짜 기준 최신순으로 조회합니다.
          공개 여부와 별점·본문 입력 여부에 관계없이 포함합니다. 날짜별 앨범은 MusicBrainz Release Group 기준으로 중복 제거하며 커버는 최대 5개를 반환합니다.
          year를 생략하면 한국 시간 기준 현재 연도를 조회합니다. hasAnyRecords로 전체 기록 없음과 선택 연도 기록 없음을 구분할 수 있습니다.
          """)
  public ApiResponse<ArchiveTimelineResponse> getTimeline(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "조회 연도. 생략 시 한국 시간 기준 현재 연도",
              schema = @Schema(type = "integer", example = "2026"))
          @RequestParam(required = false)
          String year) {
    int selectedYear = parseYear(year);
    ArchiveTimelineResponse response =
        archiveTimelineService.getTimeline(Long.valueOf(jwt.getSubject()), selectedYear);
    return ApiResponse.success("시간순 아카이브 조회에 성공했습니다.", response);
  }

  private int parseYear(String year) {
    if (year == null) {
      return Year.now(KOREA_ZONE).getValue();
    }
    if (!year.matches("[0-9]{4}")) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    try {
      int parsed = Integer.parseInt(year);
      if (parsed == 0) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }
}
