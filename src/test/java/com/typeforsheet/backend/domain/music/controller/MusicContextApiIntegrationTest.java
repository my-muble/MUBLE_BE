package com.typeforsheet.backend.domain.music.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.music.service.MusicIdService;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MusicContextApiIntegrationTest {
  private static final String GROUP_A = "a9999999-1234-1234-1234-123456789abc";
  private static final String ALIAS_A = "b9999999-1234-1234-1234-123456789abc";
  private static final String GROUP_B = "c9999999-1234-1234-1234-123456789abc";
  private static final String RECORDING = "d9999999-1234-1234-1234-123456789abc";
  private static final String RELEASE_A = "e9999999-1234-1234-1234-123456789abc";
  private static final String RELEASE_B = "f9999999-1234-1234-1234-123456789abc";
  private static HttpServer server;

  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @Autowired MusicIdService ids;
  @Autowired MusicItemRepository items;
  @Autowired MusicExternalIdRepository mappings;
  @Autowired MusicRecordRepository records;
  @Autowired UserRepository users;
  @Autowired JwtTokenProvider tokens;

  @DynamicPropertySource
  static void fixture(DynamicPropertyRegistry registry) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getRawQuery();
          if (path.equals("/ws/2/release-group/" + ALIAS_A)) {
            exchange
                .getResponseHeaders()
                .add("Location", "/ws/2/release-group/" + GROUP_A + "?fmt=json&inc=artist-credits");
            exchange.sendResponseHeaders(301, -1);
          } else if (path.startsWith("/release-group/")) {
            exchange.sendResponseHeaders(200, -1);
          } else {
            String json;
            if (path.equals("/ws/2/release-group/" + GROUP_A))
              json =
                  "{\"id\":\""
                      + GROUP_A
                      + "\",\"title\":\"Album A\",\"first-release-date\":\"2000\"}";
            else if (path.equals("/ws/2/release-group/" + GROUP_B))
              json =
                  "{\"id\":\""
                      + GROUP_B
                      + "\",\"title\":\"Album B\",\"first-release-date\":\"2001\"}";
            else if (path.equals("/ws/2/recording/" + RECORDING))
              json = "{\"id\":\"" + RECORDING + "\",\"title\":\"Track\",\"length\":1000}";
            else if (path.equals("/ws/2/release/" + RELEASE_A))
              json =
                  "{\"id\":\""
                      + RELEASE_A
                      + "\",\"media\":[{\"position\":1,\"tracks\":[{\"number\":\"1\",\"title\":\"Track\",\"length\":1000,\"recording\":{\"id\":\""
                      + RECORDING
                      + "\"}}]}]}";
            else if (path.equals("/ws/2/release") && query.contains("release-group="))
              json =
                  "{\"release-count\":1,\"releases\":[{\"id\":\""
                      + RELEASE_A
                      + "\",\"status\":\"Official\",\"date\":\"2000\"}]}";
            else if (path.equals("/ws/2/release") && query.contains("recording="))
              json =
                  "{\"release-count\":2,\"releases\":[{\"id\":\""
                      + RELEASE_A
                      + "\",\"title\":\"Representative A\",\"status\":\"Official\",\"date\":\"2000\",\"release-group\":{\"id\":\""
                      + GROUP_A
                      + "\",\"primary-type\":\"Album\"}},{\"id\":\""
                      + RELEASE_B
                      + "\",\"title\":\"Release B\",\"status\":\"Bootleg\",\"date\":\"2001\",\"release-group\":{\"id\":\""
                      + GROUP_B
                      + "\",\"primary-type\":\"Album\"}}]}";
            else {
              exchange.sendResponseHeaders(404, -1);
              exchange.close();
              return;
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    registry.add("app.music.musicbrainz.base-url", () -> base);
    registry.add("app.music.artwork.caa-base-url", () -> base);
    registry.add("app.music.artwork.enabled", () -> "true");
    registry.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:music_context;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
  }

  @AfterAll
  static void stopFixture() {
    if (server != null) server.stop(0);
  }

  @Test
  void albumContextsPreserveRecordingAndRecordListsIncludingAlias() throws Exception {
    long albumA =
        ids.getOrCreate(
            MusicType.ALBUM, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, ALIAS_A);
    long albumB =
        ids.getOrCreate(
            MusicType.ALBUM, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, GROUP_B);
    long track =
        ids.getOrCreate(
            MusicType.TRACK, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, RECORDING);
    User me = users.saveAndFlush(new User("context-viewer"));
    User other = users.saveAndFlush(new User("context-author"));
    String myAuth = "Bearer " + tokens.createAccessToken(me.getId());
    String otherAuth = "Bearer " + tokens.createAccessToken(other.getId());
    createRecord(track, myAuth, "mine");
    createRecord(track, otherAuth, "other");
    long itemCount = items.count(), mappingCount = mappings.count(), recordCount = records.count();

    JsonNode album = getData("/api/albums/" + albumA, myAuth);
    assertEquals(RECORDING, album.path("tracks").get(0).path("recordingId").asText());
    assertEquals(1, album.path("trackCount").asInt());
    JsonNode mine = getData("/api/music/" + track + "/records/me", myAuth);
    JsonNode others = getData("/api/music/" + track + "/records/others", myAuth);
    assertEquals(1, mine.path("totalRecordCount").asInt());
    assertEquals(1, others.path("totalRecordCount").asInt());

    for (String suffix :
        new String[] {"?albumId=" + albumA, "?albumId=" + albumB, "", "?albumId=" + albumA}) {
      JsonNode response = getData("/api/tracks/" + track + suffix, myAuth);
      assertEquals(track, response.path("trackId").asLong());
      if (suffix.contains("albumId=" + albumA)) {
        assertEquals(album.path("title"), response.path("albumTitle"));
        assertEquals(album.path("coverImageUrl"), response.path("coverImageUrl"));
        assertFalse(album.path("coverImageUrl").isNull());
        assertEquals(album.path("releaseDate"), response.path("releaseDate"));
        assertEquals(GROUP_A, response.path("releaseGroupId").asText());
      } else if (suffix.contains("albumId=" + albumB)) {
        assertEquals("Album B", response.path("albumTitle").asText());
        assertEquals(GROUP_B, response.path("releaseGroupId").asText());
        assertEquals("2001", response.path("releaseDate").asText());
        assertEquals(
            "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/release-group/"
                + GROUP_B
                + "/front-250",
            response.path("coverImageUrl").asText());
      } else {
        assertEquals("Representative A", response.path("albumTitle").asText());
        assertEquals(GROUP_A, response.path("releaseGroupId").asText());
        assertEquals("2000", response.path("releaseDate").asText());
        assertEquals(album.path("coverImageUrl"), response.path("coverImageUrl"));
      }
      assertEquals(mine, getData("/api/music/" + track + "/records/me", myAuth));
      assertEquals(others, getData("/api/music/" + track + "/records/others", myAuth));
    }
    assertEquals(itemCount, items.count());
    assertEquals(mappingCount, mappings.count());
    assertEquals(recordCount, records.count());
  }

  private void createRecord(long musicId, String authorization, String content) throws Exception {
    mvc.perform(
            post("/api/records")
                .header("Authorization", authorization)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"musicId\":"
                        + musicId
                        + ",\"rating\":4,\"content\":\""
                        + content
                        + "\",\"isPrivate\":false}"))
        .andExpect(status().isOk());
  }

  private JsonNode getData(String path, String authorization) throws Exception {
    return mapper
        .readTree(
            mvc.perform(get(path).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("data");
  }
}
