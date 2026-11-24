package com.typeforsheet.backend.domain.search.controller;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchPreviewResponse;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
import com.typeforsheet.backend.domain.search.service.MusicSearchPreviewService;
import com.typeforsheet.backend.domain.search.service.MusicSearchService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색하기", description = "MUBLE 음악 검색 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
@SecurityRequirement(name = "bearerAuth")
public class MusicSearchController {

  private final MusicSearchService musicSearchService;
  private final MusicSearchPreviewService musicSearchPreviewService;

  @GetMapping
  @Operation(summary = "통합 음악 검색", description = "곡·앨범·아티스트 검색 결과를 통합하여 반환합니다.")
  public ApiResponse<UnifiedMusicSearchResponse> searchAll(
      @Parameter(description = "검색어", example = "아이유") @RequestParam String query,
      @Parameter(description = "유형별 최대 검색 결과 수 (1~20)", example = "10")
          @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "유형별 검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    UnifiedMusicSearchResponse result = musicSearchService.searchAll(query, limit, offset);

    return ApiResponse.success("통합 검색에 성공했습니다.", result);
  }

  @GetMapping("/preview")
  @Operation(summary = "음악 검색 미리보기", description = "곡·앨범·아티스트를 각각 최대 3개 조회하고 유형별 표시 순서를 반환합니다.")
  public ApiResponse<MusicSearchPreviewResponse> searchPreview(
      @Parameter(description = "검색어", example = "아이유") @RequestParam String query) {

    MusicSearchPreviewResponse result = musicSearchPreviewService.searchPreview(query);

    return ApiResponse.success("검색 미리보기에 성공했습니다.", result);
  }

  @GetMapping("/tracks")
  @Operation(summary = "곡 검색", description = "MusicBrainz에서 곡을 검색하고 대표 앨범 및 커버 정보를 반환합니다.")
  public ApiResponse<SearchPage<Track>> searchTracks(
      @Parameter(description = "검색어", example = "밤편지") @RequestParam String query,
      @Parameter(description = "검색 결과 개수 (1~20)", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Track> result = musicSearchService.searchTracks(query, limit, offset);

    return ApiResponse.success("곡 검색에 성공했습니다.", result);
  }

  @GetMapping("/albums")
  @Operation(summary = "앨범 검색", description = "MusicBrainz에서 앨범을 검색하고 커버 이미지 정보를 반환합니다.")
  public ApiResponse<SearchPage<Album>> searchAlbums(
      @Parameter(description = "검색어", example = "Palette") @RequestParam String query,
      @Parameter(description = "검색 결과 개수 (1~20)", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Album> result = musicSearchService.searchAlbums(query, limit, offset);

    return ApiResponse.success("앨범 검색에 성공했습니다.", result);
  }

  @GetMapping("/artists")
  @Operation(summary = "아티스트 검색", description = "MusicBrainz에서 아티스트를 검색하고 관련 정보를 반환합니다.")
  public ApiResponse<SearchPage<Artist>> searchArtists(
      @Parameter(description = "검색어", example = "NCT WISH") @RequestParam String query,
      @Parameter(description = "검색 결과 개수 (1~20)", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Artist> result = musicSearchService.searchArtists(query, limit, offset);

    return ApiResponse.success("아티스트 검색에 성공했습니다.", result);
  }
}
