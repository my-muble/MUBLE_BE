package com.typeforsheet.backend.domain.archive.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
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
                List.of()));
    when(tracks.getTrack(track.getId())).thenReturn(track(track, "group-a"));
    when(tracks.getTrack(trackOnly.getId())).thenReturn(track(trackOnly, "group-b"));
    when(catalog.getAlbumDetail("group-b"))
        .thenReturn(
            new MusicBrainzCatalogProvider.AlbumDetail(
                "group-b", "Album B", "Album Artist B", null, null, null, List.of()));

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
