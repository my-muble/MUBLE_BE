package com.typeforsheet.backend.domain.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MusicSearchServiceTest {

  private MusicCatalogProvider catalogProvider;
  private AlbumArtworkProvider artworkProvider;
  private MusicSearchService service;

  @BeforeEach
  void setUp() {
    catalogProvider = mock(MusicCatalogProvider.class);
    artworkProvider = mock(AlbumArtworkProvider.class);

    service = new MusicSearchService(catalogProvider, artworkProvider);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void searchesTracksAndAddsCoverImage() {
    String recordingId = "11111111-1111-1111-1111-111111111111";
    String releaseGroupId = "22222222-2222-2222-2222-222222222222";
    String coverUrl = "https://example.com/cover.jpg";

    Track track =
        new Track(
            new ExternalReference("MUSICBRAINZ", "RECORDING", recordingId),
            "테스트 곡",
            "테스트 가수",
            null,
            "테스트 앨범",
            releaseGroupId,
            null,
            100);

    when(catalogProvider.searchTracks("테스트", 10, 0))
        .thenReturn(new SearchPage<>(List.of(track), 1, 10, 0));

    when(artworkProvider.findCoverUrl(releaseGroupId)).thenReturn(coverUrl);

    SearchPage<Track> result = service.searchTracks(" 테스트 ", 10, 0);

    assertEquals(1, result.results().size());
    assertEquals(recordingId, result.results().get(0).reference().id());
    assertEquals(coverUrl, result.results().get(0).coverImageUrl());

    verify(catalogProvider).searchTracks("테스트", 10, 0);
    verify(artworkProvider).findCoverUrl(releaseGroupId);
  }

  @Test
  void searchesAlbumsUsingReleaseGroupId() {
    String releaseGroupId = "33333333-3333-3333-3333-333333333333";

    Album album =
        new Album(
            new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", releaseGroupId),
            "테스트 앨범",
            "테스트 가수",
            null,
            "2026-01-01",
            "Album",
            null,
            95);

    when(catalogProvider.searchAlbums("앨범", 10, 0))
        .thenReturn(new SearchPage<>(List.of(album), 1, 10, 0));

    when(artworkProvider.findCoverUrl(releaseGroupId)).thenReturn(null);

    SearchPage<Album> result = service.searchAlbums("앨범", 10, 0);

    assertEquals(1, result.results().size());
    assertEquals("RELEASE_GROUP", result.results().get(0).reference().idType());
    assertNull(result.results().get(0).coverImageUrl());

    verify(artworkProvider).findCoverUrl(releaseGroupId);
  }

  @Test
  void searchesArtistsWithoutArtworkProvider() {
    Artist artist =
        new Artist(
            new ExternalReference("MUSICBRAINZ", "ARTIST", "44444444-4444-4444-4444-444444444444"),
            "NCT WISH",
            "Group",
            null,
            null,
            100);

    when(catalogProvider.searchArtists("NCT WISH", 10, 0))
        .thenReturn(new SearchPage<>(List.of(artist), 1, 10, 0));

    SearchPage<Artist> result = service.searchArtists("NCT WISH", 10, 0);

    assertEquals("NCT WISH", result.results().get(0).name());
    assertNull(result.results().get(0).imageUrl());

    verifyNoInteractions(artworkProvider);
  }

  @Test
  void rejectsInvalidSearchRequests() {
    assertThrows(BusinessException.class, () -> service.searchTracks(" ", 10, 0));

    assertThrows(BusinessException.class, () -> service.searchAlbums("album", 0, 0));

    assertThrows(BusinessException.class, () -> service.searchArtists("artist", 21, 0));

    assertThrows(BusinessException.class, () -> service.searchTracks("song", 10, -1));

    verifyNoInteractions(catalogProvider, artworkProvider);
  }

  @Test
  void returnsEmptyResults() {
    when(catalogProvider.searchTracks("없는곡", 10, 0))
        .thenReturn(new SearchPage<>(List.of(), 0, 10, 0));

    SearchPage<Track> result = service.searchTracks("없는곡", 10, 0);

    assertTrue(result.results().isEmpty());
    assertEquals(0, result.total());

    verifyNoInteractions(artworkProvider);
  }

  @Test
  void searchAllReturnsThreeTypes() {
    SearchPage<Track> tracks = new SearchPage<>(List.of(), 0, 10, 0);

    SearchPage<Album> albums = new SearchPage<>(List.of(), 0, 10, 0);

    Artist artist =
        new Artist(
            new ExternalReference("MUSICBRAINZ", "ARTIST", "55555555-5555-5555-5555-555555555555"),
            "IU",
            "Person",
            null,
            null,
            100);

    SearchPage<Artist> artists = new SearchPage<>(List.of(artist), 1, 10, 0);

    when(catalogProvider.searchTracks("아이유", 10, 0)).thenReturn(tracks);

    when(catalogProvider.searchAlbums("아이유", 10, 0)).thenReturn(albums);

    when(catalogProvider.searchArtists("아이유", 10, 0)).thenReturn(artists);

    UnifiedMusicSearchResponse result = service.searchAll("아이유", 10, 0);

    assertEquals(tracks, result.tracks());
    assertEquals(albums, result.albums());
    assertEquals(artists, result.artists());

    assertTrue(result.tracks().results().isEmpty());
    assertTrue(result.albums().results().isEmpty());
    assertEquals(1, result.artists().results().size());
    assertEquals("IU", result.artists().results().get(0).name());

    InOrder order = inOrder(catalogProvider);

    order.verify(catalogProvider).searchTracks("아이유", 10, 0);
    order.verify(catalogProvider).searchAlbums("아이유", 10, 0);
    order.verify(catalogProvider).searchArtists("아이유", 10, 0);

    verifyNoInteractions(artworkProvider);
  }

  @Test
  void searchAllRejectsBlankQuery() {
    assertThrows(BusinessException.class, () -> service.searchAll("   ", 10, 0));

    verifyNoInteractions(catalogProvider, artworkProvider);
  }

  @Test
  void searchAllRejectsInvalidLimit() {
    assertThrows(BusinessException.class, () -> service.searchAll("아이유", 21, 0));

    verifyNoInteractions(catalogProvider, artworkProvider);
  }

  @Test
  void searchAllPropagatesExternalApiFailure() {
    SearchPage<Track> tracks = new SearchPage<>(List.of(), 0, 10, 0);

    when(catalogProvider.searchTracks("아이유", 10, 0)).thenReturn(tracks);

    when(catalogProvider.searchAlbums("아이유", 10, 0))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));

    assertThrows(BusinessException.class, () -> service.searchAll("아이유", 10, 0));

    verify(catalogProvider, never()).searchArtists("아이유", 10, 0);
  }
}
