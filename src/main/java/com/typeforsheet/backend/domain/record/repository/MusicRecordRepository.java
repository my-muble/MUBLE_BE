package com.typeforsheet.backend.domain.record.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicRecordRepository extends JpaRepository<MusicRecord, Long> {
  @EntityGraph(attributePaths = {"user", "musicItem"})
  Optional<MusicRecord> findWithUserAndMusicItemById(Long id);
}
