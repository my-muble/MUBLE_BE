package com.typeforsheet.backend.domain.search.controller;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
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

  @GetMapping
  @Operation(summary = "통합 음악 검색", description = "곡, 앨범, 아티스트를 한 번에 검색합니다.")
  public ApiResponse<UnifiedMusicSearchResponse> searchAll(
      @Parameter(description = "검색어", example = "아이유") @RequestParam String query,
      @Parameter(description = "유형별 최대 검색 결과 수 (1~20)") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "유형별 검색 시작 위치") @RequestParam(defaultValue = "0") int offset) {

    UnifiedMusicSearchResponse results = musicSearchService.searchAll(query, limit, offset);

    return ApiResponse.success("통합 검색에 성공했습니다.", results);
  }

  @Operation(
      summary = "곡 검색",
      description =
          """
            외부 음악 API를 활용하여 곡을 검색합니다.

            - 유효한 MUBLE Access Token이 필요합니다.
            - 현재 MusicBrainz Recording 데이터를 사용합니다.
            - 곡은 Recording MBID 기준으로 식별합니다.
            - 같은 Recording이 여러 앨범에 수록될 수 있습니다.
            - 대표 앨범 및 커버 이미지는 없을 수 있습니다.
            - 검색만으로 MUBLE 내부 음악 ID가 생성되지 않습니다.
            - limit은 1~20, offset은 0~10000입니다.
            """)
  @GetMapping("/tracks")
  public ApiResponse<SearchPage<Track>> searchTracks(
      @Parameter(description = "검색어", example = "밤편지") @RequestParam String query,
      @Parameter(description = "검색 결과 개수", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Track> result = musicSearchService.searchTracks(query, limit, offset);

    return ApiResponse.success("곡 검색에 성공했습니다.", result);
  }

  @Operation(
      summary = "앨범 검색",
      description =
          """
            외부 음악 API를 활용하여 앨범을 검색합니다.

            - 유효한 MUBLE Access Token이 필요합니다.
            - 현재 MusicBrainz Release Group 데이터를 사용합니다.
            - 앨범은 Release Group MBID 기준으로 식별합니다.
            - 앨범·EP·싱글 등이 검색 결과에 포함될 수 있습니다.
            - Cover Art Archive에서 대표 이미지를 조회합니다.
            - 커버 이미지가 없거나 비활성화되면 null을 반환합니다.
            - 검색만으로 MUBLE 내부 음악 ID가 생성되지 않습니다.
            - limit은 1~20, offset은 0~10000입니다.
            """)
  @GetMapping("/albums")
  public ApiResponse<SearchPage<Album>> searchAlbums(
      @Parameter(description = "검색어", example = "Palette") @RequestParam String query,
      @Parameter(description = "검색 결과 개수", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Album> result = musicSearchService.searchAlbums(query, limit, offset);

    return ApiResponse.success("앨범 검색에 성공했습니다.", result);
  }

  @Operation(
      summary = "아티스트 검색",
      description =
          """
            외부 음악 API를 활용하여 아티스트를 검색합니다.

            - 유효한 MUBLE Access Token이 필요합니다.
            - 현재 MusicBrainz Artist 데이터를 사용합니다.
            - Artist MBID를 반환합니다.
            - 아티스트 프로필 이미지는 이번 작업에서 제공하지 않습니다.
            - 검색만으로 MUBLE 내부 음악 ID가 생성되지 않습니다.
            - limit은 1~20, offset은 0~10000입니다.
            """)
  @GetMapping("/artists")
  public ApiResponse<SearchPage<Artist>> searchArtists(
      @Parameter(description = "검색어", example = "NCT WISH") @RequestParam String query,
      @Parameter(description = "검색 결과 개수", example = "10") @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(description = "검색 시작 위치", example = "0") @RequestParam(defaultValue = "0")
          int offset) {

    SearchPage<Artist> result = musicSearchService.searchArtists(query, limit, offset);

    return ApiResponse.success("아티스트 검색에 성공했습니다.", result);
  }
}
