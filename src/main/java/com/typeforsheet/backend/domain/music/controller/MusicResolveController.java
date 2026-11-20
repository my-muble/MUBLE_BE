package com.typeforsheet.backend.domain.music.controller;

import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicResponse;
import com.typeforsheet.backend.domain.music.service.MusicResolveService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "음악", description = "외부 음악 ID와 MUBLE 내부 ID 매핑")
@SecurityRequirement(name = "bearerAuth")
public class MusicResolveController {
  private final MusicResolveService service;

  @PostMapping("/resolve")
  @Operation(
      summary = "음악 내부 ID 조회 또는 생성",
      description = "검색 결과의 reference를 전달합니다. 신규 매핑은 MusicBrainz 존재 확인 후 생성합니다.")
  public ApiResponse<ResolveMusicResponse> resolve(
      @Valid @RequestBody ResolveMusicRequest request) {
    return ApiResponse.success("음악 ID 조회에 성공했습니다.", service.resolve(request));
  }
}
