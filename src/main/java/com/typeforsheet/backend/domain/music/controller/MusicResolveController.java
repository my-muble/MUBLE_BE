package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.MusicSuccessSchemas;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicResponse;
import com.typeforsheet.backend.domain.music.service.MusicResolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
@Tag(name = "음악", description = "음악 내부 ID 연결 및 앨범·곡 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class MusicResolveController {
  private final MusicResolveService service;

  @PostMapping("/resolve")
  @Operation(
      summary = "MusicBrainz 외부 ID를 MUBLE 내부 ID에 연결",
      description =
          """
          검색 결과 reference(provider, idType, id)를 전달합니다. RELEASE_GROUP은 ALBUM, RECORDING은 TRACK으로 연결합니다.
          요청 MBID의 기존 매핑이 있으면 외부 조회 없이 반환합니다. 신규 MBID는 MusicBrainz에서 정규 MBID를 확인합니다.
          신규 연결 과정에서 서로 다른 내부 ID의 충돌이 확인되면 자동 병합하지 않고 409를 반환합니다.
          UUID가 잘못되거나 지원하지 않는 provider·idType은 거부합니다. 유효한 Access Token이 필요합니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "내부 ID 반환",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MusicSuccessSchemas.Resolve.class),
                examples = @ExampleObject(value = MusicApiExamples.RESOLVE_OK))),
    @ApiResponse(
        responseCode = "400",
        description = "필수값 누락, 잘못된 UUID 또는 지원하지 않는 참조",
        content =
            @Content(
                mediaType = "application/json",
                examples = {
                  @ExampleObject(name = "필수값", value = MusicApiExamples.VALIDATION),
                  @ExampleObject(name = "UUID", value = MusicApiExamples.INVALID_ID),
                  @ExampleObject(name = "미지원", value = MusicApiExamples.UNSUPPORTED),
                  @ExampleObject(name = "잘못된 본문", value = MusicApiExamples.INVALID)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.AUTH))),
    @ApiResponse(
        responseCode = "404",
        description = "MusicBrainz ID 없음",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.EXTERNAL_NOT_FOUND))),
    @ApiResponse(
        responseCode = "409",
        description = "정규 MBID와 이전 MBID의 내부 ID가 충돌함",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.MAPPING_CONFLICT))),
    @ApiResponse(
        responseCode = "500",
        description = "예상하지 못한 서버 오류",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.INTERNAL))),
    @ApiResponse(
        responseCode = "502",
        description = "MusicBrainz 조회 실패",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = MusicApiExamples.UPSTREAM)))
  })
  public com.typeforsheet.backend.global.response.ApiResponse<ResolveMusicResponse> resolve(
      @Valid @RequestBody ResolveMusicRequest request) {
    return com.typeforsheet.backend.global.response.ApiResponse.success(
        "음악 ID 조회에 성공했습니다.", service.resolve(request));
  }
}
