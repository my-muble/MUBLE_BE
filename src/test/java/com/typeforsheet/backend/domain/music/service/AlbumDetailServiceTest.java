package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.provider.*;
import com.typeforsheet.backend.global.exception.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AlbumDetailServiceTest {
  private final MusicItemRepository items = mock(MusicItemRepository.class);
  private final MusicExternalIdRepository ids = mock(MusicExternalIdRepository.class);
  private final MusicBrainzCatalogProvider catalog = mock(MusicBrainzCatalogProvider.class);
  private final AlbumArtworkProvider artwork = mock(AlbumArtworkProvider.class);
  private final AlbumDetailService service = new AlbumDetailService(items, ids, catalog, artwork);
  private static final String ID = "a1234567-1234-1234-1234-123456789abc";

  @Test
  void returnsAlbumAndHandlesMissingArtwork() {
    setupAlbum();
    when(catalog.getAlbumDetail(ID))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                ID,
                "Album",
                "Artist",
                "artist-id",
                "2020",
                "Album",
                List.of(new AlbumTrack(1, "A1", "Track", "Artist", 5000L, "recording-id")),
                true));
    when(artwork.findCoverUrl(ID)).thenThrow(new IllegalStateException());
    var result = service.getAlbum(1L);
    assertEquals(1L, result.albumId());
    assertEquals(ID, result.reference().id());
    assertEquals("Album", result.title());
    assertEquals("recording-id", result.tracks().get(0).recordingId());
    assertNull(result.coverImageUrl());
    assertEquals(1, result.trackCount());
    assertEquals(5000L, result.totalDurationMs());
    verify(catalog).getAlbumDetail(ID);
    verify(artwork).findCoverUrl(ID);
  }

  @Test
  void aggregatesAllTrackEntriesAndHandlesInvalidDurations() {
    setupAlbum();
    var cases =
        new Object[][] {
          {new Long[] {180000L, 210000L, 240000L}, 630000L},
          {new Long[] {null, 1L}, null},
          {new Long[] {null, null}, null},
          {new Long[] {-1L, 1L}, null},
          {new Long[] {0L, 1L}, 1L},
          {new Long[] {2147483647L, 1L}, 2147483648L},
          {new Long[] {Long.MAX_VALUE, 0L}, Long.MAX_VALUE},
          {new Long[] {Long.MAX_VALUE, 1L}, null},
          {new Long[] {}, null}
        };
    for (var test : cases) {
      Long[] durations = (Long[]) test[0];
      var tracks = new ArrayList<AlbumTrack>();
      for (int i = 0; i < durations.length; i++)
        tracks.add(
            new AlbumTrack(
                i < 2 ? 1 : 2, String.valueOf(i + 1), "Track", "Artist", durations[i], "same-id"));
      when(catalog.getAlbumDetail(ID))
          .thenReturn(
              new MusicBrainzCatalogProvider.AlbumDetail(
                  ID, "Album", "Artist", "artist-id", "2020", "Album", tracks, true));
      var result = service.getAlbum(1L);
      assertEquals(durations.length, result.trackCount());
      assertEquals(test[1], result.totalDurationMs());
      assertEquals(tracks, result.tracks());
    }
  }

  @Test
  void incompleteTracksDoNotProduceAggregates() {
    setupAlbum();
    var tracks = List.of(new AlbumTrack(1, "1", "Track", "Artist", 100L, "id"));
    when(catalog.getAlbumDetail(ID))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                ID, "Album", "Artist", "artist-id", "2020", "Album", tracks, false));
    var result = service.getAlbum(1L);
    assertNull(result.trackCount());
    assertNull(result.totalDurationMs());
    assertEquals(tracks, result.tracks());
  }

  @Test
  void rejectsMissingWrongTypeOrMapping() {
    assertError(ErrorCode.ALBUM_NOT_FOUND, 1L);
    when(items.findById(1L)).thenReturn(Optional.of(new MusicItem(MusicType.TRACK)));
    assertError(ErrorCode.ALBUM_NOT_FOUND, 1L);
    var item = mock(MusicItem.class);
    when(item.getId()).thenReturn(1L);
    when(item.getType()).thenReturn(MusicType.ALBUM);
    when(items.findById(1L)).thenReturn(Optional.of(item));
    assertError(ErrorCode.MUSIC_MAPPING_MISSING, 1L);
    verifyNoInteractions(catalog, artwork);
  }

  @Test
  void propagatesExternalNotFoundAndFailure() {
    setupAlbum();
    when(catalog.getAlbumDetail(ID))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    assertError(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND, 1L);
    assertError(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, 1L);
    verifyNoInteractions(artwork);
  }

  private void setupAlbum() {
    var item = mock(MusicItem.class);
    when(item.getId()).thenReturn(1L);
    when(item.getType()).thenReturn(MusicType.ALBUM);
    when(items.findById(1L)).thenReturn(Optional.of(item));
    var mapping = mock(MusicExternalId.class);
    when(mapping.getExternalId()).thenReturn(ID);
    when(ids.findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
            1L, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP))
        .thenReturn(Optional.of(mapping));
  }

  private void assertError(ErrorCode code, Long id) {
    assertEquals(
        code, assertThrows(BusinessException.class, () -> service.getAlbum(id)).getErrorCode());
  }
}
