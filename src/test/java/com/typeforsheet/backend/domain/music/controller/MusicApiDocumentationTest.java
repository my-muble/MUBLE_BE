package com.typeforsheet.backend.domain.music.controller;

import static org.junit.jupiter.api.Assertions.*;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MusicApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @Autowired JsonMapper mapper;
  @MockitoBean MusicResolveService resolve;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;

  private String auth() {
    return "Bearer " + tokens.createAccessToken(1L);
  }

  @Test
  void documentsAlbumAggregatesAndOptionalTrackAlbum() throws Exception {
    JsonNode root =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertTrue(root.path("openapi").asText().startsWith("3.1"));
    JsonNode album = root.path("components").path("schemas").path("AlbumDetailResponse");
    for (String field : new String[] {"trackCount", "totalDurationMs"}) {
      JsonNode schema = album.path("properties").path(field);
      assertTrue(schema.path("type").toString().contains("integer"), schema.toString());
      assertTrue(schema.path("type").toString().contains("null"), schema.toString());
      assertTrue(album.path("required").toString().contains(field));
      assertEquals(field.equals("trackCount") ? "int32" : "int64", schema.path("format").asText());
    }
    assertTrue(album.path("properties").has("tracks"));
    JsonNode operation = root.path("paths").path("/api/tracks/{trackId}").path("get");
    JsonNode parameter = null;
    for (JsonNode p : operation.path("parameters"))
      if (p.path("name").asText().equals("albumId")) parameter = p;
    assertNotNull(parameter);
    assertEquals("query", parameter.path("in").asText());
    assertFalse(parameter.path("required").asBoolean());
    assertEquals("integer", parameter.path("schema").path("type").asText());
    assertEquals("int64", parameter.path("schema").path("format").asText());
    assertEquals(1, parameter.path("schema").path("minimum").asInt());
    for (String status : new String[] {"200", "400", "401", "404", "500", "502"})
      assertTrue(operation.path("responses").has(status));
    JsonNode examples =
        root.path("paths")
            .path("/api/albums/{albumId}")
            .path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("examples");
    assertEquals(5, examples.size());
    examples
        .properties()
        .forEach(
            entry -> {
              JsonNode example = entry.getValue().path("value");
              assertEquals("앨범 상세 조회에 성공했습니다.", example.path("message").asText());
              JsonNode data = example.path("data");
              JsonNode count = data.path("trackCount"), total = data.path("totalDurationMs");
              if (!count.isNull()) assertEquals(count.asInt(), data.path("tracks").size());
              if (!total.isNull()) {
                long sum = 0;
                for (JsonNode track : data.path("tracks")) sum += track.path("durationMs").asLong();
                assertEquals(sum, total.asLong());
              }
            });
    JsonNode trackExamples =
        operation
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("examples");
    assertEquals(3, trackExamples.size());
    trackExamples
        .properties()
        .forEach(
            entry -> {
              JsonNode example = entry.getValue().path("value");
              assertEquals("곡 상세 조회에 성공했습니다.", example.path("message").asText());
              assertEquals("밤편지", example.path("data").path("title").asText());
              assertFalse(example.toString().contains("??"));
            });
    assertEquals(
        "Palette A",
        trackExamples.path("지정 앨범 A").path("value").path("data").path("albumTitle").asText());
    assertEquals(
        "Palette B",
        trackExamples.path("지정 앨범 B 커버 없음").path("value").path("data").path("albumTitle").asText());
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
