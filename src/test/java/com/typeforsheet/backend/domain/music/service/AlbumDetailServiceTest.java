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
                List.of(new AlbumTrack(1, "A1", "Track", "Artist", 5000L, "recording-id"))));
    when(artwork.findCoverUrl(ID)).thenThrow(new IllegalStateException());
    var result = service.getAlbum(1L);
    assertEquals(1L, result.albumId());
    assertEquals(ID, result.reference().id());
    assertEquals("Album", result.title());
    assertEquals("recording-id", result.tracks().get(0).recordingId());
    assertNull(result.coverImageUrl());
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
