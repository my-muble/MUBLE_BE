package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RecordMutationControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper mapper;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;
  private User owner;
  private User other;
  private MusicItem album;
  private MusicRecord publicRecord;
  private MusicRecord privateRecord;

  @BeforeEach
  void setup() {
    owner = users.saveAndFlush(new User("owner"));
    other = users.saveAndFlush(new User("other"));
    album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    publicRecord = records.saveAndFlush(new MusicRecord(owner, album, (byte) 6, "original", false));
    privateRecord = records.saveAndFlush(new MusicRecord(owner, album, (byte) 4, "private", true));
  }

  private String auth(User user) {
    return "Bearer " + tokens.createAccessToken(user.getId());
  }

  private Timestamp time(String column, long id) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM music_records WHERE id = ?", Timestamp.class, id);
  }

  @Test
  void requiresAuthenticationAndEnforcesPrivacyForBothMethods() throws Exception {
    mvc.perform(
            patch("/api/records/" + publicRecord.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/records/" + publicRecord.getId()))
        .andExpect(status().isUnauthorized());
    for (var method : new String[] {"PATCH", "DELETE"}) {
      for (MusicRecord record : new MusicRecord[] {publicRecord, privateRecord}) {
        var request =
            method.equals("PATCH")
                ? patch("/api/records/" + record.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                : delete("/api/records/" + record.getId());
        mvc.perform(request.header("Authorization", auth(other)))
            .andExpect(status().is(record.isPrivate() ? 404 : 403));
      }
    }
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM music_records WHERE user_id = ?", Integer.class, owner.getId()));
    verifyNoInteractions(albums, tracks);
  }

  @Test
  void appliesPatchAndPreservesCreatedAtWhileNoopKeepsUpdatedAt() throws Exception {
    long id = publicRecord.getId();
    Timestamp created = time("created_at", id);
    Timestamp updated = time("updated_at", id);
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value(3.0))
        .andExpect(jsonPath("$.data.content").value("original"));
    assertEquals(updated, time("updated_at", id));
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4.5,\"content\":\"changed\",\"isPrivate\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(jsonPath("$.data.content").value("changed"))
        .andExpect(jsonPath("$.data.isPrivate").value(true))
        .andExpect(jsonPath("$.data.musicId").value(album.getId()));
    assertEquals(created, time("created_at", id));
    Timestamp changed = time("updated_at", id);
    assertTrue(changed.after(updated));
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4.50,\"content\":\"changed\",\"isPrivate\":true}"))
        .andExpect(status().isOk());
    assertEquals(changed, time("updated_at", id));
    verifyNoInteractions(albums, tracks);
  }

  @Test
  void distinguishesMissingAndNullAndValidatesFinalState() throws Exception {
    long id = publicRecord.getId();
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").doesNotExist())
        .andExpect(jsonPath("$.data.content").value("original"));
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":0.5,\"content\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value(0.5))
        .andExpect(jsonPath("$.data.content").doesNotExist());
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"  \\u2003 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").doesNotExist());
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPrivate\":null}"))
        .andExpect(status().isBadRequest());
    for (String rating : new String[] {"0", "5.5", "3.7"}) {
      mvc.perform(
              patch("/api/records/" + id)
                  .header("Authorization", auth(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":" + rating + "}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + "a".repeat(500) + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/records/" + id)
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + "a".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsImmutableAndUnknownFields() throws Exception {
    for (String field : new String[] {"musicId", "musicType", "authorId", "userId", "unknown"}) {
      mvc.perform(
              patch("/api/records/" + publicRecord.getId())
                  .header("Authorization", auth(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"" + field + "\":1}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(delete("/api/records/0").header("Authorization", auth(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/records/not-a-number")
                .header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteKeepsIdempotencyHistoryAndDoesNotRestoreRecord() throws Exception {
    String body =
        "{\"musicId\":" + album.getId() + ",\"rating\":1,\"content\":null,\"isPrivate\":false}";
    String response =
        mvc.perform(
                post("/api/records")
                    .header("Authorization", auth(owner))
                    .header("Idempotency-Key", "deletion-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = mapper.readTree(response).path("data").path("recordId").asLong();
    mvc.perform(delete("/api/records/" + id).header("Authorization", auth(owner)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/records/" + id).header("Authorization", auth(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/records/" + id).header("Authorization", auth(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/records")
                .header("Authorization", auth(owner))
                .header("Idempotency-Key", "deletion-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECORD_ALREADY_DELETED"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM record_create_requests WHERE user_id = ? AND record_id IS NULL",
            Integer.class,
            owner.getId()));
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM music_records WHERE id = ?", Integer.class, id));
    verifyNoInteractions(albums, tracks);
  }

  @Test
  void failedDeleteRollsBackAndPreservesRecord() throws Exception {
    jdbc.execute(
        "CREATE TABLE record_delete_blocker (record_id BIGINT NOT NULL, FOREIGN KEY (record_id) REFERENCES music_records(id))");
    try {
      jdbc.update("INSERT INTO record_delete_blocker (record_id) VALUES (?)", publicRecord.getId());
      mvc.perform(
              delete("/api/records/" + publicRecord.getId()).header("Authorization", auth(owner)))
          .andExpect(status().isInternalServerError());
      assertTrue(records.existsById(publicRecord.getId()));
      assertEquals(
          "original",
          jdbc.queryForObject(
              "SELECT content FROM music_records WHERE id = ?",
              String.class,
              publicRecord.getId()));
    } finally {
      jdbc.execute("DROP TABLE record_delete_blocker");
    }
  }
}
