package com.typeforsheet.backend.domain.music.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TrackControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean TrackDetailService service;

  @Test
  void validatesAndPassesOptionalAlbumId() throws Exception {
    String auth = "Bearer " + tokens.createAccessToken(1L);
    when(service.getTrack(1L, 2L))
        .thenReturn(
            new TrackDetailResponse(
                1L,
                new ExternalReference("MUSICBRAINZ", "RECORDING", "mbid"),
                "Track",
                null,
                null,
                null,
                "Album",
                "group",
                null,
                "2020"));
    mvc.perform(get("/api/tracks/1").param("albumId", "2").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.albumTitle").value("Album"));
    verify(service).getTrack(1L, 2L);
    for (String invalid : new String[] {"", "0", "-1", "abc", "1.5", "9223372036854775808"})
      mvc.perform(get("/api/tracks/1").param("albumId", invalid).header("Authorization", auth))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void returnsAlbumMembershipAndUpstreamErrors() throws Exception {
    String auth = "Bearer " + tokens.createAccessToken(1L);
    when(service.getTrack(1L, 2L))
        .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST))
        .thenThrow(new BusinessException(ErrorCode.ALBUM_NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    mvc.perform(get("/api/tracks/1").param("albumId", "2")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/tracks/1").param("albumId", "2").header("Authorization", auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(get("/api/tracks/1").param("albumId", "2").header("Authorization", auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    mvc.perform(get("/api/tracks/1").param("albumId", "2").header("Authorization", auth))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("MUSIC_CATALOG_UNAVAILABLE"));
  }

  @Test
  void requiresAuthenticationAndReturnsTrack() throws Exception {
    mvc.perform(get("/api/tracks/1")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
    when(service.getTrack(1L))
        .thenReturn(
            new TrackDetailResponse(
                1L,
                new ExternalReference("MUSICBRAINZ", "RECORDING", "mbid"),
                "Track",
                "Artist",
                "artist",
                1234L,
                null,
                null,
                null,
                "2017-04-21"));
    String auth = "Bearer " + tokens.createAccessToken(1L);
    mvc.perform(get("/api/tracks/1").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.trackId").value(1))
        .andExpect(jsonPath("$.data.reference.id").value("mbid"))
        .andExpect(jsonPath("$.data.durationMs").value(1234))
        .andExpect(jsonPath("$.data.releaseDate").value("2017-04-21"));
    mvc.perform(get("/api/tracks/not-a-number").header("Authorization", auth))
        .andExpect(status().isBadRequest());
  }
}
