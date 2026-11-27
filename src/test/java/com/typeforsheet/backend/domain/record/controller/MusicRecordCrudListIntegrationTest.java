package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MusicRecordCrudListIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository musicItems;
  @Autowired JwtTokenProvider tokens;
  @Autowired JsonMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory entityManagerFactory;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean TrackDetailService tracks;

  private User a;
  private User b;
  private MusicItem album;
  private MusicItem track;
  private MusicItem anotherAlbum;

  @BeforeEach
  void setup() {
    a = users.saveAndFlush(new User("author-a"));
    b = users.saveAndFlush(new User("reader-b"));
    album = musicItems.saveAndFlush(new MusicItem(MusicType.ALBUM));
    track = musicItems.saveAndFlush(new MusicItem(MusicType.TRACK));
    anotherAlbum = musicItems.saveAndFlush(new MusicItem(MusicType.ALBUM));
    when(albums.getAlbum(anyLong()))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    when(tracks.getTrack(anyLong()))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
  }

  @Test
  void creationPoliciesIdempotencyAndMusicIsolationAreVisibleThroughApis() throws Exception {
    String key = UUID.randomUUID().toString();
    String publicBody = body(album.getId(), "4.5", "\"both\"", false);
    JsonNode first = create(a, key, publicBody, 200);
    long publicId = first.path("recordId").asLong();
    assertEquals(publicId, create(a, key, publicBody, 200).path("recordId").asLong());
    assertList(a, album, "me", 1, publicId);
    assertList(b, album, "others", 1, publicId);

    long text = create(a, body(album.getId(), "null", "\"text only\"", false));
    long rating = create(a, body(album.getId(), "3", "null", false));
    JsonNode blank =
        create(
            a,
            UUID.randomUUID().toString(),
            body(album.getId(), "4", "\" \\t\\n\\u00a0\\u2003 \"", false),
            200);
    assertTrue(blank.path("content").isNull());
    long blankId = blank.path("recordId").asLong();
    long privateBoth = create(a, body(album.getId(), "4", "\"private\"", true));
    long privateText = create(a, body(album.getId(), "null", "\"private text\"", true));
    long privateRating = create(a, body(album.getId(), "2", "null", true));
    create(
        a, UUID.randomUUID().toString(), body(album.getId(), "null", "\" \\u2003 \"", false), 400);
    assertList(
        a,
        album,
        "me",
        7,
        publicId,
        text,
        rating,
        blankId,
        privateBoth,
        privateText,
        privateRating);
    JsonNode others = assertList(b, album, "others", 2, publicId, text);
    JsonNode detail = detail(b, publicId, 200);
    JsonNode author = find(others, publicId);
    for (String field : List.of("authorId", "authorNickname", "authorProfileImageUrl")) {
      assertTrue(detail.has(field));
      assertEquals(author.path(field), detail.path(field));
    }
    for (JsonNode item : others.path("results")) {
      assertEquals(a.getId().longValue(), item.path("authorId").asLong());
      assertEquals("author-a", item.path("authorNickname").asString());
      assertTrue(item.has("authorProfileImageUrl"));
      assertTrue(item.path("authorProfileImageUrl").isNull());
    }
    assertList(a, album, "others", 0);

    long aTrack = create(a, body(track.getId(), "null", "\"track\"", false));
    long bTrack = create(b, body(track.getId(), "1", "\"other track\"", false));
    long otherAlbum = create(a, body(anotherAlbum.getId(), "1", "\"different album\"", false));
    assertList(a, track, "me", 1, aTrack);
    assertList(a, track, "others", 1, bTrack);
    assertList(b, track, "others", 1, aTrack);
    assertList(b, anotherAlbum, "others", 1, otherAlbum);
    assertList(b, album, "others", 2, publicId, text);
    verify(albums, times(1)).getAlbum(album.getId());
    verifyNoInteractions(tracks);
  }

  @Test
  void mutationsVisibilityAndDeletionUpdateBothListsWithoutChangingCreationOrder()
      throws Exception {
    long first = create(a, body(album.getId(), "4", "\"first\"", false));
    long second = create(a, body(album.getId(), "3", "\"second\"", false));
    JsonNode before = detail(a, first, 200);
    JsonNode orderedBefore = list(a, album, "me", 20, null);
    List<Long> idsBefore = ids(orderedBefore);
    JsonNode changed = patch(a, first, "{\"rating\":4.5,\"content\":\"edited\"}", 200);
    assertEquals(before.path("createdAt").asString(), changed.path("createdAt").asString());
    assertNotEquals(before.path("updatedAt").asString(), changed.path("updatedAt").asString());
    JsonNode mine = assertList(a, album, "me", 2, first, second);
    assertEquals(idsBefore, ids(mine));
    JsonNode item = find(mine, first);
    assertEquals(4.5, item.path("rating").asDouble());
    assertEquals("edited", item.path("content").asString());
    assertEquals(before.path("createdAt").asString(), item.path("createdAt").asString());
    assertEquals(
        "edited",
        find(assertList(b, album, "others", 2, first, second), first).path("content").asString());

    patch(b, first, "{\"content\":\"forbidden\"}", 403);
    remove(b, first, 403);
    assertList(a, album, "me", 2, first, second);
    assertList(b, album, "others", 2, first, second);
    patch(a, first, "{\"isPrivate\":true}", 200);
    assertList(b, album, "others", 1, second);
    detail(b, first, 404);
    patch(a, first, "{\"isPrivate\":false}", 200);
    assertList(b, album, "others", 2, first, second);
    patch(a, first, "{\"content\":null}", 200);
    assertList(b, album, "others", 1, second);
    patch(a, first, "{\"content\":\" \\t\\u2003 \"}", 200);
    assertTrue(find(assertList(a, album, "me", 2, first, second), first).path("content").isNull());
    assertList(b, album, "others", 1, second);
    patch(a, first, "{\"content\":\"restored\"}", 200);
    assertList(b, album, "others", 2, first, second);
    remove(a, first, 200);
    detail(a, first, 404);
    detail(b, first, 404);
    assertList(a, album, "me", 1, second);
    assertList(b, album, "others", 1, second);
    remove(a, second, 200);
    assertList(a, album, "me", 0);
    assertList(b, album, "others", 0);
  }

  @Test
  void detailFetchReusesJoinedAuthorWithoutExtraSql() throws Exception {
    long id = create(a, body(album.getId(), "4", "\"review\"", false));
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    boolean enabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();
    try {
      JsonNode result = detail(b, id, 200);
      assertEquals("author-a", result.path("authorNickname").asString());
      assertEquals(2, stats.getPrepareStatementCount());
      verify(albums, times(1)).getAlbum(album.getId());
      verifyNoInteractions(tracks);
    } finally {
      stats.setStatisticsEnabled(enabled);
    }
  }

  @Test
  void apiCreatedRecordsPaginateAcrossTiesAndReflectLaterChanges() throws Exception {
    List<Long> created = new ArrayList<>();
    for (int i = 0; i < 12; i++)
      created.add(create(a, body(album.getId(), "null", "\"review\"", false)));
    // Only the timestamps are fixed for deterministic equal-time and microsecond cursor boundaries.
    LocalDateTime tied = LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123456000);
    for (long id : created)
      jdbc.update("UPDATE music_records SET created_at = ? WHERE id = ?", tied, id);
    JsonNode preview = list(a, album, "me", 3, null);
    assertEquals(12, preview.path("totalRecordCount").asInt());
    assertEquals(3, preview.path("results").size());
    assertEquals(
        created.getLast().longValue(), preview.path("results").get(0).path("recordId").asLong());
    assertEquals(
        "2026-01-01T10:00:00.123456", preview.path("nextCursor").path("createdAt").asString());
    JsonNode othersPreview = list(b, album, "others", 10, null);
    assertEquals(12, othersPreview.path("totalRecordCount").asInt());
    assertEquals(10, othersPreview.path("results").size());
    assertTrue(othersPreview.path("hasNext").asBoolean());
    JsonNode othersLast = list(b, album, "others", 10, othersPreview.path("nextCursor"));
    assertEquals(12, othersLast.path("totalRecordCount").asInt());
    assertEquals(2, othersLast.path("results").size());
    assertFalse(othersLast.path("hasNext").asBoolean());
    assertTrue(othersLast.path("nextCursor").isNull());
    Set<Long> othersSeen = new HashSet<>(ids(othersPreview));
    for (long id : ids(othersLast)) assertTrue(othersSeen.add(id));
    assertEquals(new HashSet<>(created), othersSeen);
    Set<Long> seen = new HashSet<>();
    JsonNode page = preview;
    while (true) {
      assertEquals(12, page.path("totalRecordCount").asInt());
      for (long id : ids(page)) assertTrue(seen.add(id), "duplicate record " + id);
      if (!page.path("hasNext").asBoolean()) {
        assertTrue(page.path("nextCursor").isNull());
        break;
      }
      page = list(a, album, "me", 3, page.path("nextCursor"));
    }
    assertEquals(new HashSet<>(created), seen);
    assertEquals(created.reversed(), ids(list(a, album, "me", 20, null)));
    patch(a, created.getFirst(), "{\"content\":\"updated\"}", 200);
    assertEquals(created.reversed(), ids(list(a, album, "me", 20, null)));
    long newest = create(a, body(album.getId(), "4", "\"new\"", false));
    assertEquals(
        newest, list(a, album, "me", 3, null).path("results").get(0).path("recordId").asLong());
    JsonNode afterOldCursor = list(a, album, "me", 3, preview.path("nextCursor"));
    assertEquals(13, afterOldCursor.path("totalRecordCount").asInt());
    assertFalse(ids(afterOldCursor).contains(newest));
    patch(a, newest, "{\"isPrivate\":true}", 200);
    assertEquals(12, list(b, album, "others", 10, null).path("totalRecordCount").asInt());
    remove(a, created.getFirst(), 200);
    assertEquals(11, list(b, album, "others", 10, null).path("totalRecordCount").asInt());
    assertEquals(12, list(a, album, "me", 3, null).path("totalRecordCount").asInt());
    verifyNoInteractions(albums, tracks);
  }

  @Test
  void authorFetchDoesNotAddQueriesForAdditionalAuthors() throws Exception {
    User c = users.saveAndFlush(new User("author-c"));
    create(a, body(album.getId(), "1", "\"a\"", false));
    create(c, body(album.getId(), "1", "\"c\"", false));
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    boolean enabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();
    try {
      JsonNode result = list(b, album, "others", 10, null);
      assertEquals(2, result.path("totalRecordCount").asInt());
      assertEquals(4, stats.getPrepareStatementCount());
    } finally {
      stats.setStatisticsEnabled(enabled);
    }
  }

  private String auth(User user) {
    return "Bearer " + tokens.createAccessToken(user.getId());
  }

  private String body(long musicId, String rating, String content, boolean isPrivate) {
    return "{\"musicId\":"
        + musicId
        + ",\"rating\":"
        + rating
        + ",\"content\":"
        + content
        + ",\"isPrivate\":"
        + isPrivate
        + "}";
  }

  private long create(User user, String body) throws Exception {
    return create(user, UUID.randomUUID().toString(), body, 200).path("recordId").asLong();
  }

  private JsonNode create(User user, String key, String body, int status) throws Exception {
    var response =
        mvc.perform(
                post("/api/records")
                    .header("Authorization", auth(user))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("data");
  }

  private JsonNode patch(User user, long id, String body, int status) throws Exception {
    var response =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/records/" + id)
                    .header("Authorization", auth(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("data");
  }

  private void remove(User user, long id, int status) throws Exception {
    mvc.perform(delete("/api/records/" + id).header("Authorization", auth(user)))
        .andExpect(status().is(status));
  }

  private JsonNode detail(User user, long id, int status) throws Exception {
    var response =
        mvc.perform(get("/api/records/" + id).header("Authorization", auth(user)))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("data");
  }

  private JsonNode list(User user, MusicItem item, String scope, int limit, JsonNode cursor)
      throws Exception {
    var request =
        get("/api/music/" + item.getId() + "/records/" + scope)
            .header("Authorization", auth(user))
            .param("limit", Integer.toString(limit));
    if (cursor != null)
      request
          .param("cursorCreatedAt", cursor.path("createdAt").asString())
          .param("cursorId", cursor.path("recordId").asString());
    return mapper
        .readTree(
            mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("data");
  }

  private JsonNode assertList(User user, MusicItem item, String scope, int count, Long... expected)
      throws Exception {
    JsonNode result = list(user, item, scope, 50, null);
    assertEquals(count, result.path("totalRecordCount").asInt());
    assertEquals(Set.of(expected), new HashSet<>(ids(result)));
    assertEquals(count, result.path("results").size());
    return result;
  }

  private List<Long> ids(JsonNode page) {
    List<Long> result = new ArrayList<>();
    page.path("results").forEach(item -> result.add(item.path("recordId").asLong()));
    return result;
  }

  private JsonNode find(JsonNode page, long id) {
    for (JsonNode item : page.path("results"))
      if (item.path("recordId").asLong() == id) return item;
    fail("record missing: " + id);
    return null;
  }
}
