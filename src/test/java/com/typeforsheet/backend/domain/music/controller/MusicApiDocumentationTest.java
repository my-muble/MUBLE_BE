package com.typeforsheet.backend.domain.music.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.service.*;
import com.typeforsheet.backend.global.exception.*;
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
class MusicApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean MusicResolveService resolve;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;

  private String auth() {
    return "Bearer " + tokens.createAccessToken(1L);
  }

  @Test
  void generatedOpenApiGroupsOperationsAndDocumentsExamples() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/music/resolve'].post.tags[0]").value("음악"))
        .andExpect(jsonPath("$.paths['/api/albums/{albumId}'].get.tags[0]").value("음악"))
        .andExpect(jsonPath("$.paths['/api/tracks/{trackId}'].get.tags[0]").value("음악"))
        .andExpect(jsonPath("$.paths['/api/search'].get.tags[0]").value("검색하기"))
        .andExpect(jsonPath("$.paths['/api/music/resolve'].post.security[0].bearerAuth").isArray())
        .andExpect(
            jsonPath(
                    "$.paths['/api/albums/{albumId}'].get.responses['500'].content['application/json'].examples")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.MusicExternalReference.properties.id.description")
                .exists());
  }

  @Test
  void pathConversionAndUnexpectedErrorsUseActualEnvelope() throws Exception {
    for (String id : new String[] {"not-a-number", "9223372036854775808"}) {
      mvc.perform(get("/api/albums/" + id).header("Authorization", auth()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.data").doesNotExist());
      mvc.perform(get("/api/tracks/" + id).header("Authorization", auth()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    when(albums.getAlbum(1L)).thenThrow(new IllegalStateException("test"));
    mvc.perform(get("/api/albums/1").header("Authorization", auth()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
  }

  @Test
  void validationAndBusinessErrorsUseActualHttpEnvelopes() throws Exception {
    mvc.perform(
            post("/api/music/resolve")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.data.id").exists());
    when(resolve.resolve(any())).thenThrow(new BusinessException(ErrorCode.INVALID_MUSIC_ID));
    mvc.perform(
            post("/api/music/resolve")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"bad\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MUSIC_ID"));
    when(albums.getAlbum(1L)).thenThrow(new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
    mvc.perform(get("/api/albums/1").header("Authorization", auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    when(tracks.getTrack(1L))
        .thenThrow(
            new BusinessException(ErrorCode.TRACK_NOT_FOUND),
            new BusinessException(ErrorCode.MUSIC_MAPPING_MISSING),
            new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND),
            new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    mvc.perform(get("/api/tracks/1").header("Authorization", auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    mvc.perform(get("/api/tracks/1").header("Authorization", auth()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("MUSIC_MAPPING_MISSING"));
    mvc.perform(get("/api/tracks/1").header("Authorization", auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MUSIC_EXTERNAL_ID_NOT_FOUND"));
    mvc.perform(get("/api/tracks/1").header("Authorization", auth()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("MUSIC_CATALOG_UNAVAILABLE"));
  }
}
