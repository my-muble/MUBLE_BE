package com.typeforsheet.backend.domain.record.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MusicRecordRepository extends JpaRepository<MusicRecord, Long> {
  @EntityGraph(attributePaths = {"user", "musicItem"})
  Optional<MusicRecord> findWithUserAndMusicItemById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM MusicRecord r WHERE r.id = :id")
  Optional<MusicRecord> findByIdForUpdate(@Param("id") Long id);
}
