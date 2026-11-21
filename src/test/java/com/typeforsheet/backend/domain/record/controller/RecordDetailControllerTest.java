package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
class RecordDetailControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;
  private User owner;
  private User other;
  private MusicItem album;
  private MusicItem track;
  private MusicRecord publicRecord;
  private MusicRecord privateRecord;

  @BeforeEach
  void setUp() {
    owner = users.saveAndFlush(new User("owner"));
    other = users.saveAndFlush(new User("other"));
    album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    publicRecord = records.saveAndFlush(new MusicRecord(owner, album, (byte) 9, "review", false));
    privateRecord = records.saveAndFlush(new MusicRecord(owner, track, null, "private", true));
    when(albums.getAlbum(album.getId()))
        .thenAnswer(
            invocation -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return new AlbumDetailResponse(
                  album.getId(), null, "album", "artist", null, null, null, "cover", List.of());
            });
    when(tracks.getTrack(track.getId()))
        .thenAnswer(
            invocation -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return new TrackDetailResponse(
                  track.getId(), null, "track", "artist", null, null, null, null, null, null);
            });
  }

  private String auth(User user) {
    return "Bearer " + tokens.createAccessToken(user.getId());
  }

  @Test
  void authenticatedUsersSeePublicAndOwnerSeesPrivate() throws Exception {
    mvc.perform(get("/api/records/" + publicRecord.getId())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/records/" + publicRecord.getId()).header("Authorization", auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recordId").value(publicRecord.getId()))
        .andExpect(jsonPath("$.data.authorId").value(owner.getId()))
        .andExpect(jsonPath("$.data.music.musicId").value(album.getId()))
        .andExpect(jsonPath("$.data.music.type").value("ALBUM"))
        .andExpect(jsonPath("$.data.music.title").value("album"))
        .andExpect(jsonPath("$.data.music.artistName").value("artist"))
        .andExpect(jsonPath("$.data.music.coverImageUrl").value("cover"))
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(jsonPath("$.data.content").value("review"))
        .andExpect(jsonPath("$.data.isPrivate").value(false))
        .andExpect(jsonPath("$.data.isOwner").value(true))
        .andExpect(jsonPath("$.data.createdAt").exists())
        .andExpect(jsonPath("$.data.updatedAt").exists());
    mvc.perform(get("/api/records/" + publicRecord.getId()).header("Authorization", auth(other)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isOwner").value(false));
    mvc.perform(get("/api/records/" + privateRecord.getId()).header("Authorization", auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isPrivate").value(true))
        .andExpect(jsonPath("$.data.music.type").value("TRACK"))
        .andExpect(jsonPath("$.data.music.title").value("track"));
  }

  @Test
  void privateRecordsAreHiddenAndNeverFetchExternalMetadata() throws Exception {
    mvc.perform(get("/api/records/" + privateRecord.getId()).header("Authorization", auth(other)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    verifyNoInteractions(albums, tracks);
    mvc.perform(get("/api/records/" + Long.MAX_VALUE).header("Authorization", auth(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    mvc.perform(get("/api/records/0").header("Authorization", auth(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/records/abc").header("Authorization", auth(owner)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void catalogFailureLeavesLocalRecordAvailableForAlbumAndTrack() throws Exception {
    when(albums.getAlbum(anyLong()))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    when(tracks.getTrack(anyLong()))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND));
    mvc.perform(get("/api/records/" + publicRecord.getId()).header("Authorization", auth(other)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.music.musicId").value(album.getId()))
        .andExpect(jsonPath("$.data.music.type").value("ALBUM"))
        .andExpect(jsonPath("$.data.music.title").doesNotExist())
        .andExpect(jsonPath("$.data.music.artistName").doesNotExist())
        .andExpect(jsonPath("$.data.music.coverImageUrl").doesNotExist())
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(jsonPath("$.data.content").value("review"));
    mvc.perform(get("/api/records/" + privateRecord.getId()).header("Authorization", auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.music.musicId").value(track.getId()))
        .andExpect(jsonPath("$.data.music.type").value("TRACK"))
        .andExpect(jsonPath("$.data.music.title").doesNotExist())
        .andExpect(jsonPath("$.data.content").value("private"));
  }

  @Test
  void databaseAndInternalMappingErrorsAreNotMasked() throws Exception {
    when(albums.getAlbum(anyLong())).thenThrow(new DataAccessResourceFailureException("db down"));
    mvc.perform(get("/api/records/" + publicRecord.getId()).header("Authorization", auth(owner)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    doThrow(new BusinessException(ErrorCode.MUSIC_MAPPING_MISSING))
        .when(albums)
        .getAlbum(anyLong());
    mvc.perform(get("/api/records/" + publicRecord.getId()).header("Authorization", auth(owner)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("MUSIC_MAPPING_MISSING"));
  }
}
