package com.typeforsheet.backend.domain.record.repository;

import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicRecordRepository extends JpaRepository<MusicRecord, Long> {}
