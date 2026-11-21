package com.typeforsheet.backend.domain.music.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.dto.ResolveMusicResponse;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.service.MusicResolveService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MusicResolveControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean MusicResolveService service;

  @Test
  void requiresAuthenticationAndReturnsEnvelope() throws Exception {
    String body =
        "{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"}";
    mvc.perform(post("/api/music/resolve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
    when(service.resolve(any())).thenReturn(new ResolveMusicResponse(42L, MusicType.TRACK));
    mvc.perform(
            post("/api/music/resolve")
                .header("Authorization", "Bearer " + tokens.createAccessToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.musicId").value(42))
        .andExpect(jsonPath("$.data.type").value("TRACK"));
  }

  @Test
  void reportsMappingConflictWithoutChangingResponseEnvelope() throws Exception {
    when(service.resolve(any())).thenThrow(new BusinessException(ErrorCode.MUSIC_MAPPING_CONFLICT));
    mvc.perform(
            post("/api/music/resolve")
                .header("Authorization", "Bearer " + tokens.createAccessToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("MUSIC_MAPPING_CONFLICT"));
  }
}
