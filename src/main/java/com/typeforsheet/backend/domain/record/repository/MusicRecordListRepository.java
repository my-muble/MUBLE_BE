package com.typeforsheet.backend.domain.record.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Music-specific list queries. Callers request limit + 1 when checking for a next page. */
@Repository
@RequiredArgsConstructor
public class MusicRecordListRepository {
  // Match RecordValidation.content, including Unicode spaces and ASCII control whitespace.
  private static final List<String> WHITESPACE =
      IntStream.rangeClosed(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
          .filter(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
          .mapToObj(Character::toString)
          .toList();

  private final EntityManager entityManager;

  public List<MusicRecord> findMine(
      Long musicId, Long userId, LocalDateTime cursorCreatedAt, Long cursorId, int maxResults) {
    return find(musicId, userId, cursorCreatedAt, cursorId, maxResults, false);
  }

  public List<MusicRecord> findOthers(
      Long musicId, Long userId, LocalDateTime cursorCreatedAt, Long cursorId, int maxResults) {
    return find(musicId, userId, cursorCreatedAt, cursorId, maxResults, true);
  }

  public long countMine(Long musicId, Long userId) {
    return count(musicId, userId, false);
  }

  public long countOthers(Long musicId, Long userId) {
    return count(musicId, userId, true);
  }

  private List<MusicRecord> find(
      Long musicId,
      Long userId,
      LocalDateTime cursorCreatedAt,
      Long cursorId,
      int maxResults,
      boolean others) {
    if ((cursorCreatedAt == null) != (cursorId == null) || maxResults <= 0) {
      throw new IllegalArgumentException(
          "Both cursor fields and a positive result limit are required");
    }
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<MusicRecord> query = cb.createQuery(MusicRecord.class);
    Root<MusicRecord> record = query.from(MusicRecord.class);
    record.fetch("user");
    List<Predicate> conditions = filters(cb, record, musicId, userId, others);
    if (cursorCreatedAt != null) {
      conditions.add(
          cb.or(
              cb.lessThan(record.get("createdAt"), cursorCreatedAt),
              cb.and(
                  cb.equal(record.get("createdAt"), cursorCreatedAt),
                  cb.lessThan(record.get("id"), cursorId))));
    }
    query
        .select(record)
        .where(conditions.toArray(Predicate[]::new))
        .orderBy(cb.desc(record.get("createdAt")), cb.desc(record.get("id")));
    return entityManager.createQuery(query).setMaxResults(maxResults).getResultList();
  }

  private long count(Long musicId, Long userId, boolean others) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<MusicRecord> record = query.from(MusicRecord.class);
    query
        .select(cb.count(record))
        .where(filters(cb, record, musicId, userId, others).toArray(Predicate[]::new));
    return entityManager.createQuery(query).getSingleResult();
  }

  private List<Predicate> filters(
      CriteriaBuilder cb, Root<MusicRecord> record, Long musicId, Long userId, boolean others) {
    List<Predicate> conditions = new ArrayList<>();
    conditions.add(cb.equal(record.get("musicItem").get("id"), musicId));
    if (others) {
      conditions.add(cb.notEqual(record.get("user").get("id"), userId));
      conditions.add(cb.isFalse(record.get("isPrivate")));
      Expression<String> content = record.get("content");
      Expression<String> remaining = content;
      for (String whitespace : WHITESPACE) {
        remaining =
            cb.function("replace", String.class, remaining, cb.literal(whitespace), cb.literal(""));
      }
      conditions.add(cb.isNotNull(content));
      conditions.add(cb.greaterThan(cb.length(remaining), 0));
    } else {
      conditions.add(cb.equal(record.get("user").get("id"), userId));
    }
    return conditions;
  }
}
