package com.typeforsheet.backend.domain.record.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.entity.RecordCreateRequest;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MusicRecordPersistenceTest {
  @Autowired UserRepository users;
  @Autowired MusicItemRepository musicItems;
  @Autowired MusicRecordRepository records;
  @Autowired RecordCreateRequestRepository requests;
  @Autowired JdbcTemplate jdbc;

  @Test
  void storesMultipleRecordsPerMusicAndBothMusicTypes() {
    User user = users.save(new User("user"));
    MusicItem album = musicItems.save(new MusicItem(MusicType.ALBUM));
    MusicItem track = musicItems.save(new MusicItem(MusicType.TRACK));
    MusicRecord first = records.saveAndFlush(new MusicRecord(user, album, (byte) 1, null, false));
    MusicRecord second = records.saveAndFlush(new MusicRecord(user, album, null, "text", true));
    MusicRecord third =
        records.saveAndFlush(new MusicRecord(user, track, (byte) 10, "text", false));

    assertNotEquals(first.getId(), second.getId());
    assertEquals(
        MusicType.ALBUM, records.findById(second.getId()).orElseThrow().getMusicItem().getType());
    assertEquals(
        MusicType.TRACK, records.findById(third.getId()).orElseThrow().getMusicItem().getType());
    assertEquals(first.getCreatedAt(), first.getUpdatedAt());
  }

  @Test
  void preservesRequestAfterRecordDeletionAndScopesKeysByUser() {
    User user = users.save(new User("user"));
    User other = users.save(new User("other"));
    MusicItem album = musicItems.save(new MusicItem(MusicType.ALBUM));
    MusicRecord record = records.saveAndFlush(new MusicRecord(user, album, (byte) 1, null, false));
    byte[] key = "Key".getBytes(StandardCharsets.UTF_8);
    RecordCreateRequest request =
        requests.saveAndFlush(
            new RecordCreateRequest(user, key, new byte[32], record.getId(), "{}"));
    requests.saveAndFlush(new RecordCreateRequest(other, key, new byte[32], record.getId(), "{}"));
    requests.saveAndFlush(
        new RecordCreateRequest(
            user, "key".getBytes(StandardCharsets.UTF_8), new byte[32], record.getId(), "{}"));

    assertEquals(request.getCreatedAt().plusDays(30), request.getExpiresAt());
    assertTrue(requests.findByUser_IdAndIdempotencyKey(user.getId(), key).isPresent());
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO record_create_requests (user_id, idempotency_key, request_hash, record_id, response_snapshot, created_at, expires_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                user.getId(),
                key,
                new byte[32],
                record.getId(),
                "{}"));

    jdbc.update("DELETE FROM music_records WHERE id = ?", record.getId());
    assertNull(
        jdbc.queryForObject(
            "SELECT record_id FROM record_create_requests WHERE id = ?",
            Long.class,
            request.getId()));
    assertEquals(
        "{}",
        jdbc.queryForObject(
            "SELECT response_snapshot FROM record_create_requests WHERE id = ?",
            String.class,
            request.getId()));
  }

  @Test
  void databaseRejectsInvalidRatingAndEmptyRecord() {
    User user = users.save(new User("user"));
    MusicItem music = musicItems.save(new MusicItem(MusicType.TRACK));
    String sql =
        "INSERT INTO music_records (user_id, music_item_id, rating_half_steps, content, is_private, created_at, updated_at) VALUES (?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    assertCheckConstraint(sql, user.getId(), music.getId(), 0, "chk_music_records_rating");
    assertCheckConstraint(sql, user.getId(), music.getId(), 11, "chk_music_records_rating");
    assertCheckConstraint(sql, user.getId(), music.getId(), null, "chk_music_records_content");
  }

  private void assertCheckConstraint(
      String sql, Long userId, Long musicId, Integer rating, String constraint) {
    DataAccessException exception =
        assertThrows(
            DataAccessException.class, () -> jdbc.update(sql, userId, musicId, rating, null));
    assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
    SQLException cause = (SQLException) exception.getMostSpecificCause();
    assertTrue(cause.getErrorCode() == 3819 || "23513".equals(cause.getSQLState()));
    assertTrue(cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains(constraint));
  }
}
