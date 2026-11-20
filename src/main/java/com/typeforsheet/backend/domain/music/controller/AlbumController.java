package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
@Tag(name = "앨범", description = "앨범 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class AlbumController {
  private final AlbumDetailService service;

  @GetMapping("/{albumId}")
  @Operation(
      summary = "앨범 상세 조회",
      description =
          "MUBLE 내부 앨범 ID로 조회합니다. 수록곡의 recordingId와 artistId는 MusicBrainz 외부 ID입니다. durationMs는 밀리초이며 커버와 일부 메타데이터는 없을 수 있습니다.")
  public ApiResponse<AlbumDetailResponse> getAlbum(@PathVariable Long albumId) {
    return ApiResponse.success("앨범 상세 조회에 성공했습니다.", service.getAlbum(albumId));
  }
}
