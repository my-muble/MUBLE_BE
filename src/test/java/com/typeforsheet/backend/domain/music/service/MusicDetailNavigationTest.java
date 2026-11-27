package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MusicDetailNavigationTest {
  private static final String ALBUM = "a1234567-1234-1234-1234-123456789abc";
  private static final String TRACK = "b1234567-1234-1234-1234-123456789abc";
  @Autowired MusicIdService musicIds;
  @Autowired MusicResolveService resolve;
  @Autowired AlbumDetailService albums;
  @Autowired TrackDetailService tracks;
  @Autowired MusicExternalIdRepository mappings;
  @Autowired MusicItemRepository items;
  @MockitoBean MusicBrainzCatalogProvider catalog;
  @MockitoBean AlbumArtworkProvider artwork;

  @BeforeEach
  void clean() {
    mappings.deleteAll();
    items.deleteAll();
  }

  @Test
  void resolvesTrackAndAlbumIdentifiersFromDetailResponses() {
    Long albumId =
        musicIds.getOrCreate(
            MusicType.ALBUM, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, ALBUM);
    when(catalog.getAlbumDetail(ALBUM))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                ALBUM,
                "Album",
                "Artist",
                null,
                "2020",
                "Album",
                List.of(new AlbumTrack(1, "1", "Track", "Artist", 1000L, TRACK)),
                true));
    when(catalog.resolveCanonicalId("recording", TRACK)).thenReturn(TRACK);
    when(catalog.getTrackDetail(TRACK))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                TRACK, "Track", "Artist", null, 1000L, "Album", ALBUM, "2020"));

    var album = albums.getAlbum(albumId);
    String recordingId = album.tracks().get(0).recordingId();
    var resolvedTrack =
        resolve.resolve(new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", recordingId));
    assertEquals(MusicType.TRACK, resolvedTrack.type());
    var track = tracks.getTrack(resolvedTrack.musicId());
    var resolvedAlbum =
        resolve.resolve(
            new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", track.releaseGroupId()));
    assertEquals(albumId, resolvedAlbum.musicId());
    assertEquals(MusicType.ALBUM, resolvedAlbum.type());
    assertEquals(2, items.count());
    assertEquals(2, mappings.count());
    verify(catalog).resolveCanonicalId("recording", TRACK);
    verify(catalog, never()).resolveCanonicalId("release-group", ALBUM);
  }
}
