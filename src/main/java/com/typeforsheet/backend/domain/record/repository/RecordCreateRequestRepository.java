package com.typeforsheet.backend.domain.record.repository;

import com.typeforsheet.backend.domain.record.entity.RecordCreateRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordCreateRequestRepository extends JpaRepository<RecordCreateRequest, Long> {
  Optional<RecordCreateRequest> findByUser_IdAndIdempotencyKey(Long userId, byte[] idempotencyKey);
}
