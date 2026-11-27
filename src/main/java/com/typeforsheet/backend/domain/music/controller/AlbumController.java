package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.dto.MusicSuccessSchemas;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
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
@RequestMapping("/api/albums")
@RequiredArgsConstructor
@Tag(name = "음악", description = "음악 내부 ID 연결 및 앨범·곡 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class AlbumController {
  private final AlbumDetailService service;

  @GetMapping("/{albumId}")
  @Operation(
      summary = "MUBLE 내부 ID로 앨범 상세 조회",
      description =
          """
          albumId는 MusicBrainz MBID가 아닌 MUBLE 내부 ALBUM ID입니다. Release Group 정보와 대표 Release의 수록곡을 반환합니다.
          대표 Release는 공식 발매를 우선하고 발매일이 빠른 순, 동점이면 Release MBID 순으로 선택합니다.
          수록곡의 recordingId는 MusicBrainz 외부 ID이며 내부 곡 ID를 생성하지 않습니다. durationMs는 밀리초입니다.
          trackCount와 totalDurationMs는 전체 Release Group이 아닌 대표 Release의 모든 디스크를 집계합니다. 동일 Recording도 트랙마다 계산하며 집계만을 위한 추가 외부 호출은 없습니다.
          커버가 없거나 조회에 실패하면 null입니다. 유효한 Access Token이 필요합니다.
          """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "앨범 상세",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MusicSuccessSchemas.Album.class),
                examples = {
                  @ExampleObject(name = "단일 디스크", value = MusicApiExamples.ALBUM_OK),
                  @ExampleObject(name = "여러 디스크", value = MusicApiExamples.ALBUM_MULTI),
                  @ExampleObject(name = "시간 누락", value = MusicApiExamples.ALBUM_MISSING_DURATION),
                  @ExampleObject(name = "빈 목록", value = MusicApiExamples.ALBUM_EMPTY),
                  @ExampleObject(name = "정보 불완전", value = MusicApiExamples.ALBUM_INCOMPLETE)
                })),
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
        description = "내부 앨범 또는 외부 Release Group 없음",
        content =
            @Content(
                mediaType = "application/json",
                examples = {
                  @ExampleObject(name = "내부 ID", value = MusicApiExamples.ALBUM_NOT_FOUND),
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
  public ApiResponse<AlbumDetailResponse> getAlbum(
      @Parameter(
              description = "MUBLE 내부 ALBUM ID",
              example = "123",
              required = true,
              schema = @Schema(type = "integer", format = "int64"))
          @PathVariable
          String albumId) {
    Long id;
    try {
      id = Long.valueOf(albumId);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
    return ApiResponse.success("앨범 상세 조회에 성공했습니다.", service.getAlbum(id));
  }
}
