package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.dto.CreateRecordRequest;
import com.typeforsheet.backend.domain.record.dto.CreateRecordResponse;
import com.typeforsheet.backend.domain.record.service.RecordCreateService;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RecordCreateControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired JwtTokenProvider tokens;
  @Autowired RecordCreateService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper mapper;
  private User user;
  private MusicItem album;
  private MusicItem track;
  private String auth;

  @BeforeEach
  void setup() {
    user = users.saveAndFlush(new User("creator"));
    album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    track = items.saveAndFlush(new MusicItem(MusicType.TRACK));
    auth = "Bearer " + tokens.createAccessToken(user.getId());
  }

  private ResultActions create(String key, String body) throws Exception {
    var request =
        post("/api/records")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (key != null) request.header("Idempotency-Key", key);
    return mvc.perform(request);
  }

  private String body(long musicId, String rating, String content, String visibility) {
    return "{\"musicId\":"
        + musicId
        + ",\"rating\":"
        + rating
        + ",\"content\":"
        + content
        + ",\"isPrivate\":"
        + visibility
        + "}";
  }

  @Test
  void requiresAuthenticationAndKey() throws Exception {
    mvc.perform(post("/api/records").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    create(null, body(album.getId(), "1", "null", "false")).andExpect(status().isBadRequest());
    create(" ", body(album.getId(), "1", "null", "false")).andExpect(status().isBadRequest());
    create("\u00a0", body(album.getId(), "1", "null", "false")).andExpect(status().isBadRequest());
    create("x".repeat(129), body(album.getId(), "1", "null", "false"))
        .andExpect(status().isBadRequest());
    create("x".repeat(128), body(album.getId(), "1", "null", "false")).andExpect(status().isOk());
  }

  @Test
  void createsAlbumAndTrackAndAllowsMultipleRecordsForMusic() throws Exception {
    create("album1", body(album.getId(), "0.5", "null", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.musicType").value("ALBUM"))
        .andExpect(jsonPath("$.data.rating").value(0.5));
    create("album2", body(album.getId(), "null", "\"text\"", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("text"))
        .andExpect(jsonPath("$.data.isPrivate").value(true));
    create("track", body(track.getId(), "5.0", "null", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.musicType").value("TRACK"));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM music_records WHERE user_id = ? AND music_item_id = ?",
            Integer.class,
            user.getId(),
            album.getId()));
  }

  @Test
  void validatesRatingsContentAndMusic() throws Exception {
    for (String rating : new String[] {"0", "5.5", "3.7", "-0.5"}) {
      create("invalid" + rating, body(album.getId(), rating, "null", "false"))
          .andExpect(status().isBadRequest());
    }
    create("empty", body(album.getId(), "null", "null", "false"))
        .andExpect(status().isBadRequest());
    create("blank", body(album.getId(), "null", "\"  \\u2003  \"", "false"))
        .andExpect(status().isBadRequest());
    create("blank-rated", body(album.getId(), "1", "\"  \\u2003  \"", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").doesNotExist());
    create("500", body(album.getId(), "null", "\"" + "a".repeat(500) + "\"", "false"))
        .andExpect(status().isOk());
    create("501", body(album.getId(), "null", "\"" + "a".repeat(501) + "\"", "false"))
        .andExpect(status().isBadRequest());
    create("missing-private", body(album.getId(), "1", "null", "null"))
        .andExpect(status().isBadRequest());
    create("missing-music", body(Long.MAX_VALUE, "1", "null", "false"))
        .andExpect(status().isNotFound());
  }

  @Test
  void replayUsesOriginalSnapshotAndRejectsChangedOrDeletedRecord() throws Exception {
    String input = body(album.getId(), "4.5", "\"original\"", "false");
    String first =
        create("same", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long recordId = mapper.readTree(first).path("data").path("recordId").asLong();
    jdbc.update("UPDATE music_records SET content = ? WHERE id = ?", "changed", recordId);
    String replay =
        create("same", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(mapper.readTree(first), mapper.readTree(replay));
    String normalizedReplay =
        create("same", body(album.getId(), "4.50", "\"original\"", "false"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(mapper.readTree(first), mapper.readTree(normalizedReplay));
    create("same", body(album.getId(), "4", "\"original\"", "false"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    jdbc.update("DELETE FROM music_records WHERE id = ?", recordId);
    create("same", input)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECORD_ALREADY_DELETED"));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM music_records WHERE id = ?", Integer.class, recordId));
  }

  @Test
  void sameKeyIsIndependentForDifferentUsers() throws Exception {
    String input = body(album.getId(), "1", "null", "false");
    String first =
        create("per-user", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    User other = users.saveAndFlush(new User("other"));
    auth = "Bearer " + tokens.createAccessToken(other.getId());
    String second =
        create("per-user", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotEquals(
        mapper.readTree(first).path("data").path("recordId").asLong(),
        mapper.readTree(second).path("data").path("recordId").asLong());
  }

  @Test
  void expiredKeyCanBeSafelyReplaced() throws Exception {
    String input = body(album.getId(), "1", "null", "false");
    String first =
        create("expired", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    jdbc.update(
        "UPDATE record_create_requests SET expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE user_id = ?",
        user.getId());
    String next =
        create("expired", input)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotEquals(
        mapper.readTree(first).path("data").path("recordId").asLong(),
        mapper.readTree(next).path("data").path("recordId").asLong());
  }

  @Test
  void simultaneousRequestsCreateOnlyOneRecord() throws Exception {
    int count = 6;
    ExecutorService pool = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    var results = new ArrayList<Future<CreateRecordResponse>>();
    try {
      for (int i = 0; i < count; i++) {
        results.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("start timeout");
                  return service.create(
                      user.getId(),
                      "parallel",
                      new CreateRecordRequest(album.getId(), new BigDecimal("4.5"), "text", false));
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      Long id = results.get(0).get(30, TimeUnit.SECONDS).recordId();
      for (var result : results) assertEquals(id, result.get(30, TimeUnit.SECONDS).recordId());
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM music_records WHERE user_id = ?", Integer.class, user.getId()));
    } finally {
      pool.shutdownNow();
    }
  }
}
