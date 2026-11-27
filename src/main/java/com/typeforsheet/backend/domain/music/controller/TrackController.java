package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.MusicSuccessSchemas;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
@Tag(name = "음악", description = "음악 내부 ID 연결 및 앨범·곡 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class TrackController {
  private final TrackDetailService service;

  @GetMapping("/{trackId}")
  @Operation(
      summary = "MUBLE 내부 ID로 곡 상세 조회",
      description =
          """
          trackId는 MusicBrainz MBID가 아닌 MUBLE 내부 TRACK ID입니다. 연결된 Recording MBID로 MusicBrainz를 조회합니다.
          선택적 albumId는 내부 ALBUM ID이며 지정하면 해당 Release Group에 수록된 곡인지 확인하고 앨범 상세와 같은 제목, 커버, 최초 발매일을 반환합니다.
          albumId를 생략하면 기존 대표 앨범을 반환합니다. 곡의 내부 ID와 기록 범위는 변경되지 않습니다.
          대표 앨범은 공식 발매, 아티스트 일치, 컴필레이션·라이브·리믹스 등이 아닌 유형과 Album/EP/Single에 점수를 부여하고,
          동점이면 이른 발매일과 Release MBID 순으로 선택합니다. 앨범이 없으면 albumTitle, releaseGroupId, coverImageUrl은 null입니다.
          releaseDate는 albumTitle과 동일한 대표 Release의 발매일이며 정보가 없으면 null입니다.
          커버가 없거나 조회에 실패해도 null이며 durationMs는 밀리초입니다. 다른 내부 음악 ID를 생성하지 않습니다. Access Token이 필요합니다.
          """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "곡 상세",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MusicSuccessSchemas.Track.class),
                examples = @ExampleObject(value = MusicApiExamples.TRACK_OK))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "숫자가 아니거나 Long 범위 밖 또는 0 이하 ID",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.INVALID))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.AUTH))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "내부 곡 또는 외부 Recording 없음",
        content =
            @Content(
                mediaType = "application/json",
                examples = {
                  @ExampleObject(name = "내부 ID", value = MusicApiExamples.TRACK_NOT_FOUND),
                  @ExampleObject(name = "외부 ID", value = MusicApiExamples.EXTERNAL_NOT_FOUND)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "매핑 누락 또는 서버 오류",
        content =
            @Content(
                mediaType = "application/json",
                examples = {
                  @ExampleObject(name = "매핑 누락", value = MusicApiExamples.MAPPING),
                  @ExampleObject(name = "서버 오류", value = MusicApiExamples.INTERNAL)
                })),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "MusicBrainz 조회 실패",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.UPSTREAM)))
  })
  public ApiResponse<TrackDetailResponse> getTrack(
      @Parameter(
              description = "MUBLE 내부 TRACK ID",
              example = "456",
              required = true,
              schema = @Schema(type = "integer", format = "int64"))
          @PathVariable
          String trackId,
      @Parameter(
              description = "선택적 MUBLE 내부 앨범 ID",
              schema = @Schema(type = "integer", format = "int64"))
          @RequestParam(required = false)
          String albumId) {
    Long id;
    try {
      id = Long.valueOf(trackId);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
    Long album = null;
    if (albumId != null) {
      try {
        album = Long.valueOf(albumId);
      } catch (NumberFormatException exception) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
      }
      if (album <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    return ApiResponse.success(
        "곡 상세 조회에 성공했습니다.", album == null ? service.getTrack(id) : service.getTrack(id, album));
  }
}
