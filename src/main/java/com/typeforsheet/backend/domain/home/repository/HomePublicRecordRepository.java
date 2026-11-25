package com.typeforsheet.backend.domain.home.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface HomePublicRecordRepository extends Repository<MusicRecord, Long> {

  @Query(
      """
        SELECT r
        FROM MusicRecord r
        JOIN FETCH r.user
        JOIN FETCH r.musicItem
        WHERE r.isPrivate = false
          AND r.user.id <> :userId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<MusicRecord> findLatestPublicRecordsExcludingUser(
      @Param("userId") Long userId, Pageable pageable);
}
