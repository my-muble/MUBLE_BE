package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.provider.*;
import com.typeforsheet.backend.global.exception.*;
import java.util.Optional;
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
