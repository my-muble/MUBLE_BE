package com.typeforsheet.backend.domain.home.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface HomeRankingRepository extends Repository<MusicRecord, Long> {

  @Transactional(readOnly = true)
  @Query(
      """
            SELECT
              m.id AS musicId,
              m.type AS musicType,
              COUNT(r.id) AS recordCount,
              COUNT(r.ratingHalfSteps) AS ratedRecordCount,
              MAX(r.createdAt) AS latestCreatedAt
            FROM MusicRecord r
            JOIN r.musicItem m
            WHERE r.isPrivate = false
              AND r.createdAt >= :fromInclusive
              AND r.createdAt <= :toInclusive
            GROUP BY m.id, m.type
            """)
  List<HomeRankingCountRow> aggregatePublicRecords(
      @Param("fromInclusive") LocalDateTime fromInclusive,
      @Param("toInclusive") LocalDateTime toInclusive);
}
