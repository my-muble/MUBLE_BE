package com.typeforsheet.backend.domain.music.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
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
                null));
    String auth = "Bearer " + tokens.createAccessToken(1L);
    mvc.perform(get("/api/tracks/1").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.trackId").value(1))
        .andExpect(jsonPath("$.data.reference.id").value("mbid"))
        .andExpect(jsonPath("$.data.durationMs").value(1234));
    mvc.perform(get("/api/tracks/not-a-number").header("Authorization", auth))
        .andExpect(status().isBadRequest());
  }
}
