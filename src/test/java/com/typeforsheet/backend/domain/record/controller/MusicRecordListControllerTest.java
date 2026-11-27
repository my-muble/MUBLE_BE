package com.typeforsheet.backend.domain.record.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MusicRecordListControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  User me;
  User other;
  MusicItem album;
  String auth;

  @BeforeEach
  void setup() {
    me = users.saveAndFlush(new User("me"));
    other = users.saveAndFlush(new User(null));
    album = items.saveAndFlush(new MusicItem(MusicType.ALBUM));
    auth = "Bearer " + tokens.createAccessToken(me.getId());
  }

  @Test
  void mineIncludesNullsAndCursorRoundTripsMicroseconds() throws Exception {
    LocalDateTime time = LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123456000);
    MusicRecord older = add(me, (byte) 9, null, true, time.minusNanos(1000));
    MusicRecord newer = add(me, null, "text", false, time);
    String url = "/api/music/" + album.getId() + "/records/me";
    mvc.perform(get(url).header("Authorization", auth).param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.totalRecordCount").value(2))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.results[0].recordId").value(newer.getId()))
        .andExpect(jsonPath("$.data.results[0].rating", nullValue()))
        .andExpect(jsonPath("$.data.results[0].content").value("text"))
        .andExpect(jsonPath("$.data.nextCursor.createdAt").value("2026-01-01T10:00:00.123456"))
        .andExpect(jsonPath("$.data.nextCursor.recordId").value(newer.getId()));
    mvc.perform(
            get(url)
                .header("Authorization", auth)
                .param("limit", "1")
                .param("cursorCreatedAt", "2026-01-01T10:00:00.123456")
                .param("cursorId", newer.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results[0].recordId").value(older.getId()))
        .andExpect(jsonPath("$.data.results[0].rating").value(4.5))
        .andExpect(jsonPath("$.data.results[0].content", nullValue()))
        .andExpect(jsonPath("$.data.results[0].isPrivate").value(true))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor", nullValue()));
    mvc.perform(
            get(url)
                .header("Authorization", auth)
                .param("cursorCreatedAt", "2026-01-01T10:00:00.123455")
                .param("cursorId", older.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(0)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(2));
  }

  @Test
  void othersReturnsOnlyMatchingPublicRecordsAndNullAuthorFields() throws Exception {
    var date = LocalDateTime.of(2026, 1, 1, 10, 0);
    MusicRecord visible = add(other, null, "public", false, date);
    add(other, (byte) 4, null, false, date);
    add(other, (byte) 4, "private", true, date);
    add(me, (byte) 4, "mine", false, date);
    mvc.perform(
            get("/api/music/" + album.getId() + "/records/others")
                .header("Authorization", auth)
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalRecordCount").value(1))
        .andExpect(jsonPath("$.data.results", hasSize(1)))
        .andExpect(jsonPath("$.data.results[0].recordId").value(visible.getId()))
        .andExpect(jsonPath("$.data.results[0].authorId").value(other.getId()))
        .andExpect(jsonPath("$.data.results[0].authorNickname", nullValue()))
        .andExpect(jsonPath("$.data.results[0].authorProfileImageUrl", nullValue()))
        .andExpect(jsonPath("$.data.results[0].rating", nullValue()))
        .andExpect(jsonPath("$.data.results[0].content").value("public"))
        .andExpect(jsonPath("$.data.nextCursor", nullValue()));
  }

  @Test
  void defaultAndPreviewLimitsAndEmptyResults() throws Exception {
    var date = LocalDateTime.of(2026, 1, 1, 10, 0);
    for (int i = 0; i < 21; i++) add(me, (byte) 1, null, false, date.plusSeconds(i));
    String mine = "/api/music/" + album.getId() + "/records/me";
    mvc.perform(get(mine).header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(20)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(21))
        .andExpect(jsonPath("$.data.hasNext").value(true));
    mvc.perform(get(mine).header("Authorization", auth).param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(3)));
    mvc.perform(
            get("/api/music/" + album.getId() + "/records/others").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(0)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(0))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor", nullValue()));
  }

  @Test
  void othersPreviewIsLimitedToTenAndProvidesNextCursor() throws Exception {
    var date = LocalDateTime.of(2026, 1, 1, 10, 0);
    for (int i = 0; i < 11; i++) add(other, null, "public", false, date.plusSeconds(i));
    mvc.perform(
            get("/api/music/" + album.getId() + "/records/others")
                .header("Authorization", auth)
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(10)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(11))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor.recordId").exists());
  }

  @Test
  void fiveRecordsAreReturnedAcrossTwoPagesWithAnUnchangedTotal() throws Exception {
    var date = LocalDateTime.of(2026, 1, 1, 10, 0);
    MusicRecord[] created = new MusicRecord[5];
    for (int i = 0; i < created.length; i++) {
      created[i] = add(me, (byte) 9, "record " + (i + 1), false, date.plusSeconds(i));
    }
    String url = "/api/music/" + album.getId() + "/records/me";
    mvc.perform(get(url).header("Authorization", auth).param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(3)))
        .andExpect(jsonPath("$.data.results[0].recordId").value(created[4].getId()))
        .andExpect(jsonPath("$.data.results[1].recordId").value(created[3].getId()))
        .andExpect(jsonPath("$.data.results[2].recordId").value(created[2].getId()))
        .andExpect(jsonPath("$.data.totalRecordCount").value(5))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor.createdAt").value("2026-01-01T10:00:02"))
        .andExpect(jsonPath("$.data.nextCursor.recordId").value(created[2].getId()));
    mvc.perform(
            get(url)
                .header("Authorization", auth)
                .param("limit", "3")
                .param("cursorCreatedAt", "2026-01-01T10:00:02")
                .param("cursorId", created[2].getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(2)))
        .andExpect(jsonPath("$.data.results[0].recordId").value(created[1].getId()))
        .andExpect(jsonPath("$.data.results[1].recordId").value(created[0].getId()))
        .andExpect(jsonPath("$.data.totalRecordCount").value(5))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor", nullValue()));
  }

  @Test
  void othersDefaultLimitAndMaximumLimitAreApplied() throws Exception {
    var date = LocalDateTime.of(2026, 1, 1, 10, 0);
    for (int i = 0; i < 51; i++) add(other, null, "public", false, date.plusSeconds(i));
    String url = "/api/music/" + album.getId() + "/records/others";
    mvc.perform(get(url).header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(20)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(51))
        .andExpect(jsonPath("$.data.hasNext").value(true));
    mvc.perform(get(url).header("Authorization", auth).param("limit", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(50)))
        .andExpect(jsonPath("$.data.totalRecordCount").value(51))
        .andExpect(jsonPath("$.data.hasNext").value(true));
    mvc.perform(
            get("/api/music/" + album.getId() + "/records/me")
                .header("Authorization", auth)
                .param("limit", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.results", hasSize(0)));
  }

  @Test
  void validatesAuthenticationParametersAndMissingEntities() throws Exception {
    String url = "/api/music/" + album.getId() + "/records/me";
    mvc.perform(get(url))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    mvc.perform(get(url).header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    for (String limit : new String[] {"0", "-1", "51", "abc", "1.5", "", "2147483648"}) {
      mvc.perform(get(url).header("Authorization", auth).param("limit", limit))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    for (String invalidId : new String[] {"abc", "0", "-1", "9999999999999999999999"}) {
      mvc.perform(get("/api/music/" + invalidId + "/records/others").header("Authorization", auth))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    mvc.perform(
            get(url)
                .header("Authorization", auth)
                .param("cursorCreatedAt", "bad")
                .param("cursorId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    for (String invalidDate : new String[] {"2026-02-30T10:00:00", "2026/01/01 10:00:00", ""}) {
      mvc.perform(
              get(url)
                  .header("Authorization", auth)
                  .param("cursorCreatedAt", invalidDate)
                  .param("cursorId", "1"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    mvc.perform(
            get(url)
                .header("Authorization", auth)
                .param("cursorCreatedAt", "")
                .param("cursorId", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(
            get(url).header("Authorization", auth).param("cursorCreatedAt", "2026-01-01T00:00:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(get(url).header("Authorization", auth).param("cursorId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    for (String invalid : new String[] {"0", "-1", "bad", "", "9223372036854775808"}) {
      mvc.perform(
              get(url)
                  .header("Authorization", auth)
                  .param("cursorCreatedAt", "2026-01-01T00:00:00")
                  .param("cursorId", invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    mvc.perform(get("/api/music/" + Long.MAX_VALUE + "/records/me").header("Authorization", auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MUSIC_ITEM_NOT_FOUND"));
    mvc.perform(
            get(url).header("Authorization", "Bearer " + tokens.createAccessToken(Long.MAX_VALUE)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  private MusicRecord add(
      User user, Byte rating, String content, boolean isPrivate, LocalDateTime time) {
    MusicRecord record =
        records.saveAndFlush(new MusicRecord(user, album, rating, content, isPrivate));
    jdbc.update("UPDATE music_records SET created_at = ? WHERE id = ?", time, record.getId());
    entityManager.clear();
    return record;
  }
}
