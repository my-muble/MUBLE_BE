package com.typeforsheet.backend.domain.search.service;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchPreviewResponse;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MusicSearchPreviewService {

  private static final int PREVIEW_LIMIT = 3;
  private static final int CANDIDATE_LIMIT = 5;

  private static final String TRACKS = "tracks";
  private static final String ALBUMS = "albums";
  private static final String ARTISTS = "artists";

  private static final List<String> DEFAULT_ORDER = List.of(TRACKS, ALBUMS, ARTISTS);

  private final MusicSearchService musicSearchService;

  public MusicSearchPreviewResponse searchPreview(String query) {

    // 검색어 공백 제거
    String keyword = query == null ? "" : query.strip();

    // 기존 통합 검색 및 부분 실패 처리 재사용
    UnifiedMusicSearchResponse result = musicSearchService.searchAll(keyword, CANDIDATE_LIMIT, 0);

    // 유형별 중복 제거 및 최대 3개 반환
    SearchPage<Track> tracks = toPreview(result.tracks(), track -> track.reference().id());

    SearchPage<Album> albums = toPreview(result.albums(), album -> album.reference().id());

    SearchPage<Artist> artists = toPreview(result.artists(), artist -> artist.reference().id());

    // 실제 반환되는 검색 결과를 기준으로 표시 순서 결정
    List<String> displayOrder = determineDisplayOrder(keyword, tracks, albums, artists);

    return new MusicSearchPreviewResponse(tracks, albums, artists, displayOrder, result.errors());
  }

  // 유형별 중복 제거 및 노출 개수 제한
  private <T> SearchPage<T> toPreview(SearchPage<T> page, Function<T, String> idExtractor) {

    // 기존 통합 검색에서 조회에 실패한 유형
    if (page == null) {
      return null;
    }

    Set<String> seenIds = new HashSet<>();
    List<T> results = new ArrayList<>(PREVIEW_LIMIT);

    for (T item : page.results()) {

      String id = idExtractor.apply(item);

      // 동일 ID 중복 제거
      if (id != null && !seenIds.add(id)) {
        continue;
      }

      results.add(item);

      if (results.size() == PREVIEW_LIMIT) {
        break;
      }
    }

    return new SearchPage<>(List.copyOf(results), page.total(), PREVIEW_LIMIT, page.offset());
  }

  // 유형별 표시 순서 결정
  private List<String> determineDisplayOrder(
      String query,
      SearchPage<Track> tracks,
      SearchPage<Album> albums,
      SearchPage<Artist> artists) {

    List<String> exactMatches = new ArrayList<>();

    if (hasExactMatch(tracks, Track::title, query)) {
      exactMatches.add(TRACKS);
    }

    if (hasExactMatch(albums, Album::title, query)) {
      exactMatches.add(ALBUMS);
    }

    if (hasExactMatch(artists, Artist::name, query)) {
      exactMatches.add(ARTISTS);
    }

    // 정확히 일치하는 유형을 먼저 배치하고, 각 그룹 내 기본 순서 유지
    List<String> order = new ArrayList<>(exactMatches);

    // 정확히 일치하지 않는 유형은 기본 순서대로 뒤에 배치
    for (String type : DEFAULT_ORDER) {
      if (!exactMatches.contains(type)) {
        order.add(type);
      }
    }

    return order;
  }

  // 이름이 검색어와 정확히 일치하는 결과 확인
  private <T> boolean hasExactMatch(
      SearchPage<T> page, Function<T, String> nameExtractor, String query) {

    if (page == null) {
      return false;
    }

    return page.results().stream()
        .map(nameExtractor)
        .anyMatch(name -> name != null && name.strip().equalsIgnoreCase(query));
  }
}
