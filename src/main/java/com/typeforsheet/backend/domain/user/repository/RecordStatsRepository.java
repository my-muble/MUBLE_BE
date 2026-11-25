package com.typeforsheet.backend.domain.user.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RecordStatsRepository extends Repository<MusicRecord, Long> {

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.user.repository.RecordStatsRow(
            m.type,
            r.ratingHalfSteps,
            COUNT(r)
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
        GROUP BY m.type, r.ratingHalfSteps
        """)
  List<RecordStatsRow> findRecordStatsByUserId(@Param("userId") Long userId);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.user.repository.RecordStatsRow(
            m.type,
            r.ratingHalfSteps,
            COUNT(r)
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
          AND r.createdAt >= :startInclusive
          AND r.createdAt < :endExclusive
        GROUP BY m.type, r.ratingHalfSteps
        """)
  List<RecordStatsRow> findRecordStatsByUserIdAndPeriod(
      @Param("userId") Long userId,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);

  Long UserId(Long userId);
}
