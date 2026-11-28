package com.typeforsheet.backend.domain.archive.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicExternalId;
import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicProvider;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArchiveDayControllerTest {
  private static final String URL = "/api/archive/timeline/2026-09-25";
  private static final LocalDateTime START =
      LocalDate.of(2026, 9, 25)
          .atStartOfDay(ZoneId.of("Asia/Seoul"))
          .withZoneSameInstant(ZoneId.systemDefault())
          .toLocalDateTime();

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicExternalIdRepository externalIds;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;
  @MockitoBean MusicBrainzCatalogProvider catalog;

  @Test
  void groupsOnlyOwnRecordsWithinTheSelectedKoreanDay() throws Exception {
    User owner = users.saveAndFlush(new User("archive-owner"));
    User other = users.saveAndFlush(new User("archive-other"));
    MusicItem album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    mapAlbum(album, "group-a");
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    MusicItem trackOnly = items.saveAndFlush(new MusicItem(MusicType.TRACK));

    save(owner, album, (byte) 8, "oldest comment", false, START);
    save(owner, album, null, "latest comment", true, START.plusHours(10));
    save(owner, track, (byte) 10, null, true, START.plusHours(11));
    save(owner, trackOnly, null, "track-only comment", false, START.plusHours(9));
    save(owner, album, (byte) 2, "previous day", false, START.minusSeconds(1));
    save(owner, album, (byte) 2, "next day", false, START.plusDays(1));
    save(other, album, (byte) 2, "another user", false, START.plusHours(12));

    when(albums.getAlbum(album.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                album.getId(),
                new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", "group-a"),
                "Album A",
                "Artist A",
                null,
                null,
                null,
                "cover-a",
                List.of(),
                null,
                null));
    when(tracks.getTrack(track.getId())).thenReturn(track(track, "group-a"));
    when(tracks.getTrack(trackOnly.getId())).thenReturn(track(trackOnly, "group-b"));
    when(catalog.getAlbumDetail("group-b"))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                "group-b", "Album B", "Album Artist B", null, null, null, List.of(), true));

    mvc.perform(get(URL).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-09-25"))
        .andExpect(jsonPath("$.data.recordCount").value(4))
        .andExpect(jsonPath("$.data.albumCount").value(2))
        .andExpect(jsonPath("$.data.albums[0].albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.albums[0].albumId").value(album.getId()))
        .andExpect(jsonPath("$.data.albums[0].title").value("Album A"))
        .andExpect(jsonPath("$.data.albums[0].coverImageUrl").value("cover-a"))
        .andExpect(jsonPath("$.data.albums[0].recordCount").value(3))
        .andExpect(jsonPath("$.data.albums[0].averageRating").value(4.5))
        .andExpect(jsonPath("$.data.albums[0].contentPreview").value("latest comment"))
        .andExpect(jsonPath("$.data.albums[1].albumKey").value("group-b"))
        .andExpect(jsonPath("$.data.albums[1].artistName").value("Album Artist B"))
        .andExpect(jsonPath("$.data.albums[1].averageRating").doesNotExist());
  }

  @Test
  void returnsAlbumRecordsAndAllTrackRowsWithDateScopedCounts() throws Exception {
    User owner = users.saveAndFlush(new User("album-owner"));
    User other = users.saveAndFlush(new User("album-other"));
    MusicItem album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    mapAlbum(album, "group-a");
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    save(owner, album, (byte) 9, null, true, START.plusHours(9));
    save(owner, album, null, "latest private record", true, START.plusHours(10));
    save(owner, track, (byte) 8, null, false, START);
    save(owner, track, null, "second track record", true, START.plusHours(11));
    save(owner, track, (byte) 2, null, false, START.plusDays(1));
    save(other, track, (byte) 2, null, false, START);

    when(albums.getAlbum(album.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                album.getId(),
                new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", "group-a"),
                "Album A",
                "Artist A",
                null,
                null,
                null,
                "cover-a",
                List.of(
                    new AlbumTrack(1, "1", "Unrecorded", null, null, "unrecorded"),
                    new AlbumTrack(1, "2", "Recorded", null, null, "recorded")),
                null,
                null));
    when(tracks.getTrack(track.getId()))
        .thenReturn(
            new TrackDetailResponse(
                track.getId(),
                new ExternalReference("MUSICBRAINZ", "RECORDING", "recorded"),
                "Recorded",
                null,
                null,
                null,
                "Album A",
                "group-a",
                null,
                null));

    String path = URL + "/albums/group-a";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-09-25"))
        .andExpect(jsonPath("$.data.album.albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.album.coverImageUrl").value("cover-a"))
        .andExpect(jsonPath("$.data.totalRecordCount").value(4))
        .andExpect(jsonPath("$.data.albumRecordCount").value(2))
        .andExpect(jsonPath("$.data.trackRecordCount").value(2))
        .andExpect(jsonPath("$.data.albumRecords[0].createdAt").value("2026-09-25T10:00:00+09:00"))
        .andExpect(jsonPath("$.data.albumRecords[0].content").value("latest private record"))
        .andExpect(jsonPath("$.data.albumRecords[0].rating").doesNotExist())
        .andExpect(jsonPath("$.data.albumRecords[1].rating").value(4.5))
        .andExpect(jsonPath("$.data.tracks[0].trackNumber").value("1"))
        .andExpect(jsonPath("$.data.tracks[0].recordCount").value(0))
        .andExpect(jsonPath("$.data.tracks[1].trackId").value(track.getId()))
        .andExpect(jsonPath("$.data.tracks[1].recordCount").value(2));
    mvc.perform(get(URL + "/albums/unselected").header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
  }

  @Test
  void opensTrackOnlyAlbumWithoutAnInternalAlbumId() throws Exception {
    User owner = users.saveAndFlush(new User("track-only-owner"));
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    save(owner, track, (byte) 9, null, true, START);
    when(tracks.getTrack(track.getId()))
        .thenReturn(
            new TrackDetailResponse(
                track.getId(),
                new ExternalReference("MUSICBRAINZ", "RECORDING", "track-c"),
                "Track C",
                null,
                null,
                null,
                "Album C",
                "group-c",
                "cover-c",
                null));
    when(catalog.getAlbumDetail("group-c"))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                "group-c",
                "Album C",
                "Artist C",
                null,
                null,
                null,
                List.of(new AlbumTrack(1, "1", "Track C", null, null, "track-c")),
                true));
    mvc.perform(get(URL + "/albums/group-c").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.album.albumId").doesNotExist())
        .andExpect(jsonPath("$.data.album.title").value("Album C"))
        .andExpect(jsonPath("$.data.totalRecordCount").value(1))
        .andExpect(jsonPath("$.data.albumRecords").isEmpty())
        .andExpect(jsonPath("$.data.tracks[0].recordCount").value(1));
    mvc.perform(get("/api/archive/albums/group-c/records").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalRecordCount").value(1))
        .andExpect(jsonPath("$.data.albumRecordCount").value(0))
        .andExpect(jsonPath("$.data.albumRecords").isEmpty());
  }

  @Test
  void returnsOnlySelectedTrackRecordsForTheOwnerAndDate() throws Exception {
    User owner = users.saveAndFlush(new User("day-track-owner"));
    User other = users.saveAndFlush(new User("day-track-other"));
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    MusicItem sibling = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    MusicItem album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    save(owner, track, (byte) 9, null, false, START);
    save(owner, track, null, "private comment", true, START.plusHours(11));
    save(owner, track, (byte) 6, "previous day", false, START.minusSeconds(1));
    save(owner, track, (byte) 8, "next day", false, START.plusDays(1));
    save(other, track, (byte) 4, "another user", false, START.plusHours(12));
    save(owner, sibling, (byte) 4, "another track", false, START.plusHours(12));
    save(owner, album, (byte) 4, "album record", false, START.plusHours(12));

    when(tracks.getTrack(track.getId()))
        .thenReturn(
            new TrackDetailResponse(
                track.getId(),
                new ExternalReference("MUSICBRAINZ", "RECORDING", "selected-track"),
                "Recording title",
                "Artist A",
                null,
                null,
                "Album A",
                "group-a",
                "cover-a",
                null));
    when(catalog.getAlbumDetail("group-a"))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                "group-a",
                "Album A",
                "Artist A",
                null,
                null,
                null,
                List.of(new AlbumTrack(1, "2", "Track title", null, null, "selected-track")),
                true));

    String path = URL + "/albums/group-a/tracks/" + track.getId();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-09-25"))
        .andExpect(jsonPath("$.data.albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.track.trackId").value(track.getId()))
        .andExpect(jsonPath("$.data.track.discNumber").value(1))
        .andExpect(jsonPath("$.data.track.trackNumber").value("2"))
        .andExpect(jsonPath("$.data.track.title").value("Track title"))
        .andExpect(jsonPath("$.data.track.artistName").value("Artist A"))
        .andExpect(jsonPath("$.data.track.coverImageUrl").value("cover-a"))
        .andExpect(jsonPath("$.data.recordCount").value(2))
        .andExpect(jsonPath("$.data.records.length()").value(2))
        .andExpect(jsonPath("$.data.records[0].createdAt").value("2026-09-25T11:00:00+09:00"))
        .andExpect(jsonPath("$.data.records[0].content").value("private comment"))
        .andExpect(jsonPath("$.data.records[0].rating").doesNotExist())
        .andExpect(jsonPath("$.data.records[1].rating").value(4.5))
        .andExpect(jsonPath("$.data.records[1].content").doesNotExist());
    mvc.perform(get(path.replace("group-a", "wrong-album")).header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    mvc.perform(
            get(path.replace("2026-09-25", "2026-09-27")).header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    mvc.perform(get(URL + "/albums/group-a/tracks/0").header("Authorization", bearer(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void keepsAlbumGroupingAndNavigationWhenCatalogFailsAndRecovers() throws Exception {
    User owner = users.saveAndFlush(new User("archive-catalog-outage"));
    MusicItem album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    mapAlbum(album, "stable-group");
    save(owner, album, (byte) 8, null, false, START);
    save(owner, track, (byte) 9, null, true, START.plusHours(1));
    BusinessException outage = new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    when(albums.getAlbum(album.getId())).thenThrow(outage);
    when(tracks.getTrack(track.getId())).thenThrow(outage);

    mvc.perform(get(URL).header("Authorization", bearer(owner)))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("MUSIC_CATALOG_UNAVAILABLE"));

    org.mockito.Mockito.doReturn(track(track, "stable-group")).when(tracks).getTrack(track.getId());
    mvc.perform(get(URL).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albumCount").value(1))
        .andExpect(jsonPath("$.data.albums[0].albumKey").value("stable-group"));

    when(tracks.getTrack(track.getId())).thenThrow(outage);
    String albumPath = URL + "/albums/stable-group";
    String trackPath = albumPath + "/tracks/" + track.getId();
    mvc.perform(get(URL).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albumCount").value(1))
        .andExpect(jsonPath("$.data.albums[0].recordCount").value(2))
        .andExpect(jsonPath("$.data.albums[0].albumKey").value("stable-group"));
    mvc.perform(get(albumPath).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trackRecordCount").value(1));
    mvc.perform(get(trackPath).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recordCount").value(1));

    org.mockito.Mockito.doReturn(track(track, "changed-group"))
        .when(tracks)
        .getTrack(track.getId());
    mvc.perform(get(trackPath).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albumKey").value("stable-group"));
  }

  @Test
  void returnsAllTimeAlbumArchiveWithTrackRecordsGroupedIntoTheirAlbum() throws Exception {
    User owner = users.saveAndFlush(new User("album-archive-owner"));
    User other = users.saveAndFlush(new User("album-archive-other"));
    MusicItem albumA = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    MusicItem albumB = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    MusicItem trackA = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    mapAlbum(albumA, "group-a");
    mapAlbum(albumB, "group-b");

    save(owner, albumA, (byte) 8, null, true, START.minusYears(1));
    save(owner, trackA, null, "track note", false, START.minusDays(1));
    save(owner, trackA, (byte) 10, null, true, START);
    save(owner, albumB, null, "latest album", false, START.plusDays(1));
    save(other, albumB, (byte) 2, null, false, START.plusDays(2));
    save(other, albumA, (byte) 2, null, false, START.plusDays(3));

    when(albums.getAlbum(albumA.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                albumA.getId(),
                new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", "group-a"),
                "Album A",
                "Artist A",
                null,
                null,
                null,
                "cover-a",
                List.of(
                    new AlbumTrack(1, "1", "No records", null, null, "track-zero"),
                    new AlbumTrack(1, "2", "Track A", null, null, "track-a"))));
    when(albums.getAlbum(albumB.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                albumB.getId(),
                new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", "group-b"),
                "Album B",
                "Artist B",
                null,
                null,
                null,
                "cover-b",
                List.of()));
    when(tracks.getTrack(trackA.getId()))
        .thenReturn(
            new TrackDetailResponse(
                trackA.getId(),
                new ExternalReference("MUSICBRAINZ", "RECORDING", "track-a"),
                "Track A",
                "Artist A",
                null,
                null,
                "Album A",
                "group-a",
                "cover-a",
                null));

    String path = "/api/archive/albums";
    mvc.perform(get(path).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albumCount").value(2))
        .andExpect(jsonPath("$.data.albums[0].albumKey").value("group-b"))
        .andExpect(jsonPath("$.data.albums[1].albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.albums[1].recordCount").value(3));

    mvc.perform(get(path).param("sort", "MOST_RECORDED").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albums[0].albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.albums[0].albumId").value(albumA.getId()))
        .andExpect(jsonPath("$.data.albums[0].artistName").value("Artist A"))
        .andExpect(jsonPath("$.data.albums[0].coverImageUrl").value("cover-a"))
        .andExpect(jsonPath("$.data.albums[0].recordCount").value(3))
        .andExpect(jsonPath("$.data.albums[1].recordCount").value(1));

    save(owner, albumA, null, "old comment", false, START.minusDays(3));
    save(owner, albumA, (byte) 6, null, true, START.minusDays(2));
    save(owner, albumA, null, "latest comment", true, START.plusDays(1));

    mvc.perform(get(path + "/group-a")).andExpect(status().isUnauthorized());
    mvc.perform(get(path + "/group-a").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.album.albumKey").value("group-a"))
        .andExpect(jsonPath("$.data.totalRecordCount").value(6))
        .andExpect(jsonPath("$.data.albumRecordCount").value(4))
        .andExpect(jsonPath("$.data.trackRecordCount").value(2))
        .andExpect(jsonPath("$.data.albumRecords.length()").value(3))
        .andExpect(jsonPath("$.data.albumRecords[0].content").value("latest comment"))
        .andExpect(jsonPath("$.data.albumRecords[1].rating").value(3.0))
        .andExpect(jsonPath("$.data.tracks[0].recordCount").value(0))
        .andExpect(jsonPath("$.data.tracks[1].trackId").value(trackA.getId()))
        .andExpect(jsonPath("$.data.tracks[1].recordCount").value(2));

    mvc.perform(get(path + "/group-a/records").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalRecordCount").value(6))
        .andExpect(jsonPath("$.data.albumRecords.length()").value(4))
        .andExpect(jsonPath("$.data.albumRecords[3].rating").value(4.0));
    mvc.perform(get(path + "/unknown/records").header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));

    LocalDateTime koreaNewYear =
        LocalDate.of(2026, 1, 1)
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
    save(owner, trackA, null, "previous year", true, koreaNewYear.minusSeconds(1));
    save(other, trackA, (byte) 2, null, false, START.plusDays(2));

    String trackPath = path + "/group-a/tracks/";
    mvc.perform(get(trackPath + trackA.getId())).andExpect(status().isUnauthorized());
    mvc.perform(get(trackPath + trackA.getId()).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.track.trackId").value(trackA.getId()))
        .andExpect(jsonPath("$.data.track.trackNumber").value("2"))
        .andExpect(jsonPath("$.data.track.artistName").value("Artist A"))
        .andExpect(jsonPath("$.data.track.coverImageUrl").value("cover-a"))
        .andExpect(jsonPath("$.data.recordCount").value(3))
        .andExpect(jsonPath("$.data.years[0].year").value(2026))
        .andExpect(jsonPath("$.data.years[0].records[0].rating").value(5.0))
        .andExpect(jsonPath("$.data.years[0].records[1].content").value("track note"))
        .andExpect(jsonPath("$.data.years[1].year").value(2025))
        .andExpect(jsonPath("$.data.years[1].records[0].content").value("previous year"));
    mvc.perform(get(trackPath + "track-zero").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.track.recordingId").value("track-zero"))
        .andExpect(jsonPath("$.data.track.trackNumber").value("1"))
        .andExpect(jsonPath("$.data.recordCount").value(0))
        .andExpect(jsonPath("$.data.years").isEmpty());
    mvc.perform(
            get(path + "/group-b/tracks/" + trackA.getId()).header("Authorization", bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
  }

  private void mapAlbum(MusicItem album, String group) {
    externalIds.saveAndFlush(
        new MusicExternalId(album, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, group));
  }

  @Test
  void handlesEmptyDatesInvalidDatesAndUnauthenticatedRequests() throws Exception {
    User owner = users.saveAndFlush(new User("archive-empty"));
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/archive/timeline/2026-02-30").header("Authorization", bearer(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(get(URL).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recordCount").value(0))
        .andExpect(jsonPath("$.data.albumCount").value(0))
        .andExpect(jsonPath("$.data.albums").isEmpty());
  }

  private String bearer(User user) {
    return "Bearer " + tokens.createAccessToken(user.getId());
  }

  private TrackDetailResponse track(MusicItem item, String releaseGroupId) {
    return new TrackDetailResponse(
        item.getId(),
        null,
        "Track",
        "Track Artist",
        null,
        null,
        "Track Album",
        releaseGroupId,
        null,
        null);
  }

  private void save(
      User user,
      MusicItem item,
      Byte rating,
      String content,
      boolean isPrivate,
      LocalDateTime time) {
    MusicRecord record =
        records.saveAndFlush(new MusicRecord(user, item, rating, content, isPrivate));
    jdbc.update("UPDATE music_records SET created_at = ? WHERE id = ?", time, record.getId());
  }
}
