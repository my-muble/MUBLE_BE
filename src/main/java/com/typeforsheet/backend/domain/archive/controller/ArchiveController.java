package com.typeforsheet.backend.domain.archive.controller;

import com.typeforsheet.backend.domain.archive.dto.ArchiveAlbumDetailResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveAlbumListResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveAlbumSort;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayTrackResponse;
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
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
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
@RequestMapping("/api/archive")
@RequiredArgsConstructor
@Tag(name = "아카이브", description = "본인 음악 기록 아카이브 API")
@SecurityRequirement(name = "bearerAuth")
public class ArchiveController {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private final ArchiveTimelineService archiveTimelineService;

  @GetMapping("/albums")
  @Operation(
      summary = "앨범순 아카이브 조회",
      description =
          """
          본인의 전체 기간 기록을 MusicBrainz Release Group 기준의 고유 앨범으로 묶어 조회합니다.
          앨범 자체 기록과 수록곡 기록을 함께 합산하며 공개 여부와 별점·본문 입력 여부에 관계없이 포함합니다.
          LATEST는 최근 작성 시각 내림차순, MOST_RECORDED는 누적 기록 수 내림차순입니다.
          동률이면 최근 작성 시각, 앨범명 오름차순으로 정렬합니다.
          """)
  public ApiResponse<ArchiveAlbumListResponse> getAlbums(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "정렬 기준. LATEST(최신순, 기본값) 또는 MOST_RECORDED(기록 많은 순)",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {"LATEST", "MOST_RECORDED"}))
          @RequestParam(required = false)
          String sort) {
    return ApiResponse.success(
        "앨범순 아카이브 조회에 성공했습니다.",
        archiveTimelineService.getAlbums(
            Long.valueOf(jwt.getSubject()), ArchiveAlbumSort.parse(sort)));
  }

  @GetMapping("/albums/{albumKey}")
  @Operation(
      summary = "누적 앨범 기록 상세 조회",
      description =
          """
          앨범순 목록의 albumKey로 해당 앨범과 수록곡의 전체 기간 본인 기록을 집계합니다.
          공개·비공개 기록을 모두 포함하며 앨범 자체 기록은 작성 시각 최신순으로 최대 3건을 반환합니다.
          수록곡 목록은 앨범 순서대로 반환하며 기록이 없는 곡도 포함합니다.
          해당 그룹에 본인 기록이 없으면 404 ALBUM_NOT_FOUND입니다.
          """)
  public ApiResponse<ArchiveAlbumDetailResponse> getAlbumDetail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "앨범순 아카이브 목록에서 반환한 albumKey") @PathVariable String albumKey) {
    return ApiResponse.success(
        "누적 앨범 기록 상세 조회에 성공했습니다.",
        archiveTimelineService.getAlbumDetail(Long.valueOf(jwt.getSubject()), albumKey));
  }

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

  @GetMapping("/timeline/{date}")
  @Operation(
      summary = "날짜별 아카이브 상세 목록 조회",
      description =
          """
          한국 시간 기준 선택 날짜의 본인 앨범·곡 기록을 조회합니다.
          공개 여부와 무관하게 포함하며 같은 앨범의 기록은 한 행으로 묶습니다.
          앨범 행은 가장 최근 기록의 작성 시각 기준 최신순입니다.
          """)
  public ApiResponse<ArchiveDayResponse> getDay(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "조회할 기록 작성일(YYYY-MM-DD)",
              schema = @Schema(type = "string", format = "date", example = "2026-09-25"))
          @PathVariable
          String date) {
    return ApiResponse.success(
        "날짜별 아카이브 조회에 성공했습니다.",
        archiveTimelineService.getDay(Long.valueOf(jwt.getSubject()), parseDate(date)));
  }

  @GetMapping("/timeline/{date}/albums/{albumKey}")
  @Operation(
      summary = "날짜별 앨범 기록 조회",
      description =
          """
          날짜별 아카이브 상세 목록의 albumKey로 해당 날짜의 본인 앨범·수록곡 기록을 조회합니다.
          한국 시간 기준으로 공개·비공개 기록을 모두 집계합니다.
          앨범 자체 기록은 최신순이며 수록곡은 기록이 0건이어도 앨범 순서대로 반환합니다.
          해당 날짜에 선택한 앨범 그룹의 본인 기록이 없으면 404 ALBUM_NOT_FOUND입니다.
          """)
  public ApiResponse<ArchiveDayAlbumResponse> getDayAlbum(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "조회할 기록 작성일(YYYY-MM-DD)", example = "2026-09-25") @PathVariable
          String date,
      @Parameter(description = "날짜별 아카이브 응답에서 반환한 albumKey") @PathVariable String albumKey) {
    return ApiResponse.success(
        "날짜별 앨범 기록 조회에 성공했습니다.",
        archiveTimelineService.getDayAlbum(
            Long.valueOf(jwt.getSubject()), parseDate(date), albumKey));
  }

  @GetMapping("/timeline/{date}/albums/{albumKey}/tracks/{trackId}")
  @Operation(
      summary = "날짜별 수록곡 기록 조회",
      description =
          """
          ARC-004의 albumKey와 trackId로 선택 날짜(한국 시간)의 본인 수록곡 기록을 최신순으로 조회합니다.
          공개·비공개 기록을 모두 포함하며, 곡이 해당 그룹에 속하지 않거나 선택 날짜의 기록이 없으면
          404 TRACK_NOT_FOUND를 반환합니다.
          """)
  public ApiResponse<ArchiveDayTrackResponse> getDayTrack(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "조회할 기록 작성일(YYYY-MM-DD)", example = "2026-09-25") @PathVariable
          String date,
      @Parameter(description = "날짜별 앨범 기록 응답의 album.albumKey") @PathVariable String albumKey,
      @Parameter(description = "수록곡 행의 내부 trackId", example = "456") @PathVariable String trackId) {
    return ApiResponse.success(
        "날짜별 수록곡 기록 조회에 성공했습니다.",
        archiveTimelineService.getDayTrack(
            Long.valueOf(jwt.getSubject()), parseDate(date), albumKey, parseTrackId(trackId)));
  }

  private long parseTrackId(String trackId) {
    try {
      long id = Long.parseLong(trackId);
      if (id > 0) {
        return id;
      }
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
    throw new BusinessException(ErrorCode.INVALID_REQUEST);
  }

  private LocalDate parseDate(String date) {
    if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || date.startsWith("0000-")) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    try {
      return LocalDate.parse(date);
    } catch (DateTimeParseException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
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
