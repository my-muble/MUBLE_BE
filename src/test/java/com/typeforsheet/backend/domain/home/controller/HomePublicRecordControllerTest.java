package com.typeforsheet.backend.domain.home.controller;

import static org.mockito.Mockito.verify;
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
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
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
class HomePublicRecordControllerTest {
  private static final String URL = "/api/home/public-records";
  private static final LocalDateTime START = LocalDateTime.of(2099, 1, 1, 0, 0);

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;

  @Test
  void rejectsRequestsWithoutAnAccessToken() throws Exception {
    mvc.perform(get(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  void returnsTenNewestPublicRecordsFromOtherUsers() throws Exception {
    User author = users.saveAndFlush(new User("home-author"));
    User viewer = users.saveAndFlush(new User("home-viewer"));
    MusicItem album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    MusicItem track = items.saveAndFlush(new MusicItem(MusicType.TRACK));

    MusicRecord oldest = save(author, album, (byte) 8, "oldest", false, 0);
    for (int minute = 1; minute <= 7; minute++) {
      save(author, album, (byte) 8, "earlier public record", false, minute);
    }

    MusicRecord full = save(author, album, (byte) 9, "album review", false, 8);
    MusicRecord ratingOnly = save(author, track, (byte) 10, null, false, 9);
    MusicRecord contentOnly = save(author, album, null, "text only", false, 9);
    MusicRecord hidden = save(author, track, null, "private", true, 10);
    MusicRecord own = save(viewer, track, null, "own", false, 11);

    when(albums.getAlbum(album.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                album.getId(), null, "album", "artist", null, null, null, "cover", List.of()));
    when(tracks.getTrack(track.getId()))
        .thenReturn(
            new TrackDetailResponse(
                track.getId(), null, "track", "artist", null, null, null, null, null, null));

    mvc.perform(
            get(URL).header("Authorization", "Bearer " + tokens.createAccessToken(viewer.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.length()").value(10))
        .andExpect(jsonPath("$.data[0].recordId").value(contentOnly.getId()))
        .andExpect(jsonPath("$.data[0].authorNickname").value("home-author"))
        .andExpect(jsonPath("$.data[0].music.type").value("ALBUM"))
        .andExpect(jsonPath("$.data[0].music.coverImageUrl").value("cover"))
        .andExpect(jsonPath("$.data[0].contentPreview").value("text only"))
        .andExpect(jsonPath("$.data[0].rating").doesNotExist())
        .andExpect(jsonPath("$.data[1].recordId").value(ratingOnly.getId()))
        .andExpect(jsonPath("$.data[1].music.type").value("TRACK"))
        .andExpect(jsonPath("$.data[1].rating").value(5.0))
        .andExpect(jsonPath("$.data[1].contentPreview").doesNotExist())
        .andExpect(jsonPath("$.data[2].recordId").value(full.getId()))
        .andExpect(jsonPath("$.data[?(@.recordId == " + oldest.getId() + ")]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.recordId == " + hidden.getId() + ")]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.recordId == " + own.getId() + ")]").isEmpty());

    verify(albums).getAlbum(album.getId());
    verify(tracks).getTrack(track.getId());
  }

  private MusicRecord save(
      User author, MusicItem item, Byte rating, String content, boolean isPrivate, int minute) {
    MusicRecord record =
        records.saveAndFlush(new MusicRecord(author, item, rating, content, isPrivate));
    jdbc.update(
        "UPDATE music_records SET created_at = ? WHERE id = ?",
        START.plusMinutes(minute),
        record.getId());
    return record;
  }
}
