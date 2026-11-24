package com.typeforsheet.backend.domain.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchPreviewResponse;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicSearchPreviewServiceTest {

  private MusicSearchService musicSearchService;
  private MusicSearchPreviewService previewService;

  @BeforeEach
  void setUp() {
    musicSearchService = mock(MusicSearchService.class);
    previewService = new MusicSearchPreviewService(musicSearchService);
  }

  @Test
  void returnsMaximumThreeResultsPerType() {
    Track track1 = track("t1", "곡1");
    Track track2 = track("t2", "곡2");
    Track track3 = track("t3", "곡3");
    Track track4 = track("t4", "곡4");

    Album album1 = album("a1", "앨범1");
    Album album2 = album("a2", "앨범2");
    Album album3 = album("a3", "앨범3");
    Album album4 = album("a4", "앨범4");

    Artist artist1 = artist("r1", "가수1");
    Artist artist2 = artist("r2", "가수2");
    Artist artist3 = artist("r3", "가수3");
    Artist artist4 = artist("r4", "가수4");

    stubSearch(
        "아이유",
        page(List.of(track1, track2, track3, track4)),
        page(List.of(album1, album2, album3, album4)),
        page(List.of(artist1, artist2, artist3, artist4)),
        Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("아이유");

    assertEquals(3, result.tracks().results().size());
    assertEquals(3, result.albums().results().size());
    assertEquals(3, result.artists().results().size());

    assertEquals(3, result.tracks().limit());
    assertEquals(3, result.albums().limit());
    assertEquals(3, result.artists().limit());

    assertEquals(List.of(track1, track2, track3), result.tracks().results());

    assertEquals(List.of(album1, album2, album3), result.albums().results());

    assertEquals(List.of(artist1, artist2, artist3), result.artists().results());

    assertTrue(result.errors().isEmpty());
  }

  @Test
  void removesDuplicateIdsAndKeepsOriginalOrder() {
    Track first = track("t1", "밤편지");
    Track duplicate = track("t1", "밤편지");
    Track second = track("t2", "밤편지");
    Track third = track("t3", "드라마");

    stubSearch(
        "아이유",
        page(List.of(first, duplicate, second, third)),
        page(List.of()),
        page(List.of()),
        Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("아이유");

    assertEquals(List.of(first, second, third), result.tracks().results());

    assertEquals(3, result.tracks().results().size());
  }

  @Test
  void putsExactMatchingArtistFirst() {
    Track track = track("t1", "밤편지");
    Album album = album("a1", "Palette");
    Artist artist = artist("r1", "아이유");

    stubSearch("아이유", page(List.of(track)), page(List.of(album)), page(List.of(artist)), Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("아이유");

    assertEquals(List.of("artists", "tracks", "albums"), result.displayOrder());
  }

  @Test
  void comparesNamesIgnoringCase() {
    Album album = album("a1", "Palette");

    stubSearch("palette", page(List.of()), page(List.of(album)), page(List.of()), Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("palette");

    assertEquals(List.of("albums", "tracks", "artists"), result.displayOrder());
  }

  @Test
  void keepsDefaultOrderWhenMultipleTypesMatchExactly() {
    Track track = track("t1", "Palette");
    Album album = album("a1", "Palette");

    stubSearch("Palette", page(List.of(track)), page(List.of(album)), page(List.of()), Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("Palette");

    assertEquals(List.of("tracks", "albums", "artists"), result.displayOrder());
  }

  @Test
  void keepsDefaultOrderWhenNoTypeMatchesExactly() {
    Track track = track("t1", "밤편지");
    Album album = album("a1", "Palette");
    Artist artist = artist("r1", "아이유");

    stubSearch("아이", page(List.of(track)), page(List.of(album)), page(List.of(artist)), Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("아이");

    assertEquals(List.of("tracks", "albums", "artists"), result.displayOrder());
  }

  @Test
  void keepsSuccessfulResultsWhenOneTypeFails() {
    Track track = track("t1", "밤편지");
    Artist artist = artist("r1", "아이유");

    stubSearch(
        "아이유",
        page(List.of(track)),
        null,
        page(List.of(artist)),
        Map.of("albums", "MUSIC_CATALOG_UNAVAILABLE"));

    MusicSearchPreviewResponse result = previewService.searchPreview("아이유");

    assertEquals(List.of(track), result.tracks().results());

    assertNull(result.albums());

    assertEquals(List.of(artist), result.artists().results());

    assertEquals("MUSIC_CATALOG_UNAVAILABLE", result.errors().get("albums"));

    assertEquals(1, result.errors().size());
  }

  @Test
  void returnsEmptyResultsWithoutErrors() {
    stubSearch("없는검색어", page(List.of()), page(List.of()), page(List.of()), Map.of());

    MusicSearchPreviewResponse result = previewService.searchPreview("없는검색어");

    assertTrue(result.tracks().results().isEmpty());
    assertTrue(result.albums().results().isEmpty());
    assertTrue(result.artists().results().isEmpty());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void propagatesFailureWhenAllTypesFail() {
    BusinessException exception = new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);

    doReturn(null).when(musicSearchService).searchAll("테스트", 5, 0);

    // 전체 실패는 기존 통합 검색에서 예외를 발생시킨다.
    org.mockito.Mockito.reset(musicSearchService);

    org.mockito.Mockito.doThrow(exception).when(musicSearchService).searchAll("아이유", 5, 0);

    BusinessException thrown =
        assertThrows(BusinessException.class, () -> previewService.searchPreview("아이유"));

    assertSame(exception, thrown);
  }

  @Test
  void trimsQueryBeforeSearching() {
    stubSearch("아이유", page(List.of()), page(List.of()), page(List.of()), Map.of());

    previewService.searchPreview("  아이유  ");

    verify(musicSearchService).searchAll("아이유", 5, 0);
  }

  @Test
  void propagatesInvalidRequestForBlankQuery() {
    BusinessException exception = new BusinessException(ErrorCode.INVALID_REQUEST);

    org.mockito.Mockito.doThrow(exception).when(musicSearchService).searchAll("", 5, 0);

    BusinessException thrown =
        assertThrows(BusinessException.class, () -> previewService.searchPreview("   "));

    assertSame(exception, thrown);
  }

  // 통합 검색 Mock 설정을 한 곳에서 처리
  private void stubSearch(
      String query,
      SearchPage<Track> tracks,
      SearchPage<Album> albums,
      SearchPage<Artist> artists,
      Map<String, String> errors) {

    UnifiedMusicSearchResponse response =
        new UnifiedMusicSearchResponse(tracks, albums, artists, errors);

    doReturn(response).when(musicSearchService).searchAll(query, 5, 0);
  }

  // 검색 페이지 생성
  private static <T> SearchPage<T> page(List<T> items) {
    return new SearchPage<>(items, items.size(), 5, 0);
  }

  // 곡 Mock 생성
  private static Track track(String id, String title) {
    Track result = mock(Track.class);

    ExternalReference reference = new ExternalReference("MUSICBRAINZ", "RECORDING", id);

    doReturn(reference).when(result).reference();

    doReturn(title).when(result).title();

    return result;
  }

  // 앨범 Mock 생성
  private static Album album(String id, String title) {
    Album result = mock(Album.class);

    ExternalReference reference = new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", id);

    doReturn(reference).when(result).reference();

    doReturn(title).when(result).title();

    return result;
  }

  // 아티스트 Mock 생성
  private static Artist artist(String id, String name) {
    Artist result = mock(Artist.class);

    ExternalReference reference = new ExternalReference("MUSICBRAINZ", "ARTIST", id);

    doReturn(reference).when(result).reference();

    doReturn(name).when(result).name();

    return result;
  }
}
