package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
@Tag(name = "곡", description = "곡 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class TrackController {
  private final TrackDetailService service;

  @GetMapping("/{trackId}")
  @Operation(
      summary = "곡 상세 조회",
      description =
          "MUBLE 내부 곡 ID로 조회합니다. artistId와 releaseGroupId는 MusicBrainz 외부 ID이며 durationMs는 밀리초입니다. 앨범과 커버 정보는 없을 수 있습니다.")
  public ApiResponse<TrackDetailResponse> getTrack(@PathVariable String trackId) {
    Long id;
    try {
      id = Long.valueOf(trackId);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
    return ApiResponse.success("곡 상세 조회에 성공했습니다.", service.getTrack(id));
  }
}
