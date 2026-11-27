package com.typeforsheet.backend.domain.record.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MusicRecordListRepositoryTest {
  @Autowired MusicRecordListRepository lists;
  @Autowired MusicRecordRepository records;
  @Autowired MusicItemRepository musicItems;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;

  User me;
  User other;
  MusicItem album;
  MusicItem track;
  LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);

  @BeforeEach
  void setup() {
    me = users.save(new User("me"));
    other = users.save(new User("other"));
    album = musicItems.save(new MusicItem(MusicType.ALBUM));
    track = musicItems.save(new MusicItem(MusicType.TRACK));
  }

  @Test
  void mineIncludesAllRecordFormsAndVisibilityForOnlyTheSelectedMusicAndUser() {
    MusicRecord rating = add(me, album, (byte) 8, null, false, time);
    MusicRecord text = add(me, album, null, "text", true, time.plusSeconds(1));
    MusicRecord both = add(me, album, (byte) 5, "both", false, time.plusSeconds(2));
    add(other, album, (byte) 1, "other", false, time);
    add(me, track, (byte) 1, "track", false, time);
    entityManager.clear();

    assertEquals(
        List.of(both.getId(), text.getId(), rating.getId()),
        ids(lists.findMine(album.getId(), me.getId(), null, null, 4)));
    assertEquals(3, lists.countMine(album.getId(), me.getId()));
    assertEquals(1, lists.countMine(track.getId(), me.getId()));
    assertEquals(0, lists.countMine(track.getId(), other.getId()));
    assertTrue(lists.findMine(track.getId(), other.getId(), null, null, 1).isEmpty());
  }

  @Test
  void othersUseIdenticalContentVisibilityAndOwnerFiltersForListAndCount() {
    MusicRecord both = add(other, album, (byte) 8, "good", false, time);
    MusicRecord text = add(other, album, null, " \t좋아요\n\u00a0", false, time.plusSeconds(1));
    add(other, album, (byte) 8, null, false, time);
    add(other, album, (byte) 8, "private", true, time);
    add(me, album, (byte) 8, "mine", false, time);
    add(other, track, (byte) 8, "track", false, time);
    add(other, album, (byte) 8, "", false, time);
    add(other, album, (byte) 8, " \t\n\r\u00a0\u2003\u3000", false, time);
    // Exercise the complete set used by RecordValidation, not just common ASCII spaces.
    IntStream.rangeClosed(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
        .filter(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
        .forEach(c -> add(other, album, (byte) 8, Character.toString(c), false, time));
    entityManager.flush();
    entityManager.clear();

    List<MusicRecord> found = lists.findOthers(album.getId(), me.getId(), null, null, 20);
    assertEquals(List.of(text.getId(), both.getId()), ids(found));
    assertEquals(2, lists.countOthers(album.getId(), me.getId()));
    assertEquals(1, lists.countOthers(track.getId(), me.getId()));
    assertEquals(0, lists.countOthers(track.getId(), other.getId()));
    assertTrue(lists.findOthers(track.getId(), other.getId(), null, null, 1).isEmpty());
  }

  @Test
  void cursorOrdersByCreationTimeAndIdAndDoesNotAffectCount() {
    MusicRecord newest = add(me, album, (byte) 1, null, false, time.plusSeconds(1));
    MusicRecord oldest = add(me, album, (byte) 1, null, false, time.minusSeconds(1));
    MusicRecord first = add(me, album, (byte) 1, null, false, time);
    MusicRecord second = add(me, album, (byte) 1, null, false, time);
    entityManager.clear();

    List<MusicRecord> pageAndExtra = lists.findMine(album.getId(), me.getId(), null, null, 3);
    assertEquals(List.of(newest.getId(), second.getId(), first.getId()), ids(pageAndExtra));
    List<MusicRecord> next = lists.findMine(album.getId(), me.getId(), time, second.getId(), 3);
    assertEquals(List.of(first.getId(), oldest.getId()), ids(next));
    assertEquals(4, lists.countMine(album.getId(), me.getId()));
    assertTrue(
        lists
            .findMine(album.getId(), me.getId(), time.minusSeconds(1), oldest.getId(), 3)
            .isEmpty());
    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () -> lists.findMine(album.getId(), me.getId(), time, null, 2));
  }

  @Test
  void othersCursorUsesTheSameOrderingAndFilterAsCount() {
    MusicRecord older = add(other, album, null, "older", false, time);
    MusicRecord newer = add(other, album, null, "newer", false, time);
    add(other, album, (byte) 1, " \n", false, time);
    entityManager.clear();

    assertEquals(
        List.of(newer.getId(), older.getId()),
        ids(lists.findOthers(album.getId(), me.getId(), null, null, 2)));
    assertEquals(
        List.of(older.getId()),
        ids(lists.findOthers(album.getId(), me.getId(), time, newer.getId(), 2)));
    assertEquals(2, lists.countOthers(album.getId(), me.getId()));
  }

  @Test
  void cursorPreservesMicrosecondsAcrossDatabaseRoundTrip() {
    LocalDateTime earlierTime = time.plusNanos(123455000);
    LocalDateTime laterTime = time.plusNanos(123456000);
    MusicRecord earlier = add(me, album, null, "earlier", false, earlierTime);
    MusicRecord later = add(me, album, null, "later", false, laterTime);
    entityManager.clear();

    MusicRecord first = lists.findMine(album.getId(), me.getId(), null, null, 1).getFirst();
    assertEquals(later.getId(), first.getId());
    assertEquals(laterTime, first.getCreatedAt());
    List<MusicRecord> next =
        lists.findMine(album.getId(), me.getId(), first.getCreatedAt(), first.getId(), 1);
    assertEquals(List.of(earlier.getId()), ids(next));
    assertEquals(earlierTime, next.getFirst().getCreatedAt());
  }

  @Test
  void fetchesAuthorsInTheListQueryWithoutAdditionalQueries() {
    User another = users.save(new User("another"));
    add(other, album, null, "one", false, time);
    add(another, album, null, "two", false, time);
    entityManager.flush();
    entityManager.clear();
    var stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    boolean wasEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();
    try {
      List<MusicRecord> found = lists.findOthers(album.getId(), me.getId(), null, null, 10);
      assertEquals(2, found.size());
      assertTrue(found.stream().allMatch(r -> Hibernate.isInitialized(r.getUser())));
      assertEquals(
          List.of("another", "other"), found.stream().map(r -> r.getUser().getNickname()).toList());
      assertEquals(1, stats.getPrepareStatementCount());
    } finally {
      stats.setStatisticsEnabled(wasEnabled);
    }
  }

  private MusicRecord add(
      User user,
      MusicItem music,
      Byte rating,
      String content,
      boolean isPrivate,
      LocalDateTime createdAt) {
    MusicRecord record =
        records.saveAndFlush(new MusicRecord(user, music, rating, content, isPrivate));
    jdbc.update("UPDATE music_records SET created_at = ? WHERE id = ?", createdAt, record.getId());
    return record;
  }

  private List<Long> ids(List<MusicRecord> records) {
    return records.stream().map(MusicRecord::getId).toList();
  }
}
