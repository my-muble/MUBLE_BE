package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.provider.*;
import com.typeforsheet.backend.global.exception.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TrackDetailServiceTest {
  private final MusicItemRepository items = mock(MusicItemRepository.class);
  private final MusicExternalIdRepository ids = mock(MusicExternalIdRepository.class);
  private final MusicBrainzCatalogProvider catalog = mock(MusicBrainzCatalogProvider.class);
  private final AlbumArtworkProvider artwork = mock(AlbumArtworkProvider.class);
  private final TrackDetailService service = new TrackDetailService(items, ids, catalog, artwork);
  private static final String ID = "a1234567-1234-1234-1234-123456789abc";
  private static final String GROUP = "b1234567-1234-1234-1234-123456789abc";

  @Test
  void albumThenTrackReuseCoverHttpCacheAndFailureReturnsNull() throws Exception {
    setupTrack();
    var album = mock(MusicItem.class);
    when(album.getId()).thenReturn(2L);
    when(album.getType()).thenReturn(MusicType.ALBUM);
    when(items.findById(2L)).thenReturn(Optional.of(album));
    var mapping = mock(MusicExternalId.class);
    when(mapping.getExternalId()).thenReturn(GROUP);
    when(ids.findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
            2L, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP))
        .thenReturn(Optional.of(mapping));
    when(catalog.getAlbumDetail(GROUP))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                GROUP, "Album", "Artist", null, "2000", "Album", List.of(), true));
    when(catalog.getTrackDetail(ID, GROUP))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                ID, "Track", "Artist", null, 100L, "Album", GROUP, "2000"));
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(
              exchange.getRequestURI().getPath().contains(GROUP) ? 200 : 404, -1);
          exchange.close();
        });
    server.start();
    try {
      var cover =
          new CoverArtArchiveArtworkProvider(
              true, "http://127.0.0.1:" + server.getAddress().getPort());
      var albumResult = new AlbumDetailService(items, ids, catalog, cover).getAlbum(2L);
      assertEquals(1, requests.get());
      var trackResult = new TrackDetailService(items, ids, catalog, cover).getTrack(1L, 2L);
      assertEquals(albumResult.coverImageUrl(), trackResult.coverImageUrl());
      assertEquals(1, requests.get());
      assertNull(cover.findCoverUrl(ID));
      assertEquals(2, requests.get());
      assertNull(cover.findCoverUrl(ID));
      assertEquals(2, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void returnsRequestedAlbumAndRejectsInvalidAlbum() {
    setupTrack();
    var album = mock(MusicItem.class);
    when(album.getId()).thenReturn(2L);
    when(album.getType()).thenReturn(MusicType.ALBUM);
    when(items.findById(2L)).thenReturn(Optional.of(album));
    var mapping = mock(MusicExternalId.class);
    when(mapping.getExternalId()).thenReturn(GROUP);
    when(ids.findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
            2L, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP))
        .thenReturn(Optional.of(mapping));
    when(catalog.getTrackDetail(ID, GROUP))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                ID, "Track", "Artist", "artist", 1234L, "Requested", GROUP, "2001"));
    when(artwork.findCoverUrl(GROUP)).thenReturn("cover");
    var result = service.getTrack(1L, 2L);
    when(catalog.getAlbumDetail(GROUP))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                GROUP,
                "Requested",
                "Artist",
                "artist",
                "2001",
                "Album",
                java.util.List.of(),
                true));
    var albumResult = new AlbumDetailService(items, ids, catalog, artwork).getAlbum(2L);
    assertEquals("Requested", result.albumTitle());
    assertEquals("2001", result.releaseDate());
    assertEquals("cover", result.coverImageUrl());
    assertEquals(1L, result.trackId());
    assertEquals(albumResult.title(), result.albumTitle());
    assertEquals(albumResult.releaseDate(), result.releaseDate());
    assertEquals(albumResult.coverImageUrl(), result.coverImageUrl());
    when(artwork.findCoverUrl(GROUP)).thenThrow(new IllegalStateException());
    assertNull(service.getTrack(1L, 2L).coverImageUrl());
    when(catalog.getTrackDetail(ID, GROUP))
        .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
    assertEquals(
        ErrorCode.INVALID_REQUEST,
        assertThrows(BusinessException.class, () -> service.getTrack(1L, 2L)).getErrorCode());
    verify(catalog, never()).getTrackDetail(ID);
    assertEquals(
        ErrorCode.INVALID_REQUEST,
        assertThrows(BusinessException.class, () -> service.getTrack(1L, 0L)).getErrorCode());
    assertEquals(
        ErrorCode.ALBUM_NOT_FOUND,
        assertThrows(BusinessException.class, () -> service.getTrack(1L, 3L)).getErrorCode());
    verify(items, never()).save(any());
    verify(ids, never()).save(any());
  }

  @Test
  void returnsTrackAndHandlesMissingArtwork() {
    setupTrack();
    when(catalog.getTrackDetail(ID))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                ID, "Track", "Artist", "artist", 1234L, "Album", GROUP, "2017-04-21"));
    when(artwork.findCoverUrl(GROUP)).thenThrow(new IllegalStateException());
    var result = service.getTrack(1L);
    assertEquals(1L, result.trackId());
    assertEquals(ID, result.reference().id());
    assertEquals("RECORDING", result.reference().idType());
    assertEquals("Track", result.title());
    assertEquals(1234L, result.durationMs());
    assertEquals(GROUP, result.releaseGroupId());
    assertEquals("2017-04-21", result.releaseDate());
    assertNull(result.coverImageUrl());
    verify(items, never()).save(any());
    verify(ids, never()).save(any());
  }

  @Test
  void returnsTrackWithoutAlbum() {
    setupTrack();
    when(catalog.getTrackDetail(ID))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                ID, "Track", null, null, null, null, null, null));
    var result = service.getTrack(1L);
    assertNull(result.albumTitle());
    assertNull(result.releaseGroupId());
    assertNull(result.coverImageUrl());
    assertNull(result.releaseDate());
    verifyNoInteractions(artwork);
  }

  @Test
  void rejectsMissingWrongTypeAndMissingMapping() {
    assertError(ErrorCode.INVALID_REQUEST, 0L);
    assertError(ErrorCode.TRACK_NOT_FOUND, 1L);
    when(items.findById(1L)).thenReturn(Optional.of(new MusicItem(MusicType.ALBUM)));
    assertError(ErrorCode.TRACK_NOT_FOUND, 1L);
    var item = mock(MusicItem.class);
    when(item.getId()).thenReturn(1L);
    when(item.getType()).thenReturn(MusicType.TRACK);
    when(items.findById(1L)).thenReturn(Optional.of(item));
    assertError(ErrorCode.MUSIC_MAPPING_MISSING, 1L);
    verifyNoInteractions(catalog, artwork);
  }

  @Test
  void propagatesExternalNotFoundAndFailure() {
    setupTrack();
    when(catalog.getTrackDetail(ID))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    assertError(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND, 1L);
    assertError(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, 1L);
    verifyNoInteractions(artwork);
  }

  private void setupTrack() {
    var item = mock(MusicItem.class);
    when(item.getId()).thenReturn(1L);
    when(item.getType()).thenReturn(MusicType.TRACK);
    when(items.findById(1L)).thenReturn(Optional.of(item));
    var mapping = mock(MusicExternalId.class);
    when(mapping.getExternalId()).thenReturn(ID);
    when(ids.findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
            1L, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING))
        .thenReturn(Optional.of(mapping));
  }

  private void assertError(ErrorCode code, Long id) {
    assertEquals(
        code, assertThrows(BusinessException.class, () -> service.getTrack(id)).getErrorCode());
  }
}
