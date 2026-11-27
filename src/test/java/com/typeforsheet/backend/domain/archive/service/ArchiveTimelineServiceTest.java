package com.typeforsheet.backend.domain.archive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRepository;
import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArchiveTimelineServiceTest {

  private ArchiveRepository archives;
  private UserRepository users;
  private AlbumDetailService albums;
  private TrackDetailService tracks;
  private ArchiveTimelineService service;

  @BeforeEach
  void setUp() {
    archives = mock(ArchiveRepository.class);
    users = mock(UserRepository.class);
    albums = mock(AlbumDetailService.class);
    tracks = mock(TrackDetailService.class);
    service = new ArchiveTimelineService(archives, users, albums, tracks);
    when(users.existsById(1L)).thenReturn(true);
    when(archives.existsByUser_Id(1L)).thenReturn(true);
  }

  @Test
  void groupsRecordsByMonthAndDayAndDeduplicatesAlbumCovers() {
    LocalDateTime september25 = LocalDateTime.of(2026, 9, 25, 12, 0);
    when(archives.findTimelineRows(eq(1L), any(), any()))
        .thenReturn(
            List.of(
                row(7L, 11L, MusicType.ALBUM, september25),
                row(6L, 12L, MusicType.TRACK, september25.minusMinutes(1)),
                row(5L, 13L, MusicType.ALBUM, september25.minusMinutes(2)),
                row(4L, 14L, MusicType.ALBUM, september25.minusMinutes(3)),
                row(3L, 15L, MusicType.ALBUM, september25.minusMinutes(4)),
                row(2L, 16L, MusicType.ALBUM, september25.minusMinutes(5)),
                row(1L, 17L, MusicType.ALBUM, LocalDateTime.of(2026, 8, 30, 12, 0))));

    when(albums.getAlbum(11L)).thenReturn(album(11L, "rg-1", "cover-1"));
    when(tracks.getTrack(12L)).thenReturn(track(12L, "rg-1", "cover-1"));
    for (long id = 13; id <= 17; id++) {
      when(albums.getAlbum(id)).thenReturn(album(id, "rg-" + (id - 11), "cover-" + id));
    }

    var response = service.getTimeline(1L, 2026);

    assertTrue(response.hasAnyRecords());
    assertEquals(
        List.of("2026-09", "2026-08"), response.months().stream().map(m -> m.yearMonth()).toList());
    var day = response.months().getFirst().days().getFirst();
    assertEquals(6, day.recordCount());
    assertEquals(5, day.albumCount());
    assertEquals(5, day.covers().size());
    assertEquals("rg-1", day.covers().getFirst().albumKey());
    verify(albums).getAlbum(11L);
    verify(tracks).getTrack(12L);
  }

  @Test
  void distinguishesAnEmptyYearFromAnAccountWithNoRecords() {
    when(archives.findTimelineRows(eq(1L), any(), any())).thenReturn(List.of());

    var response = service.getTimeline(1L, 2026);

    assertTrue(response.hasAnyRecords());
    assertTrue(response.months().isEmpty());
  }

  private ArchiveRecordRow row(Long recordId, Long musicId, MusicType type, LocalDateTime time) {
    return new ArchiveRecordRow(recordId, musicId, type, time);
  }

  private AlbumDetailResponse album(Long id, String releaseGroupId, String cover) {
    return new AlbumDetailResponse(
        id,
        new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", releaseGroupId),
        "album",
        "artist",
        null,
        null,
        null,
        cover,
        List.of());
  }

  private TrackDetailResponse track(Long id, String releaseGroupId, String cover) {
    return new TrackDetailResponse(
        id, null, "track", "artist", null, null, "album", releaseGroupId, cover, null);
  }
}
