package com.typeforsheet.backend.domain.music.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AlbumControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean AlbumDetailService service;

  @Test
  void requiresAuthenticationAndReturnsAlbum() throws Exception {
    mvc.perform(get("/api/albums/1")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
    when(service.getAlbum(1L))
        .thenReturn(
            new AlbumDetailResponse(
                1L,
                new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", "mbid"),
                "Album",
                "Artist",
                "artist",
                "2020",
                "Album",
                null,
                List.of()));
    mvc.perform(
            get("/api/albums/1").header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.albumId").value(1))
        .andExpect(jsonPath("$.data.reference.id").value("mbid"));
  }
}
