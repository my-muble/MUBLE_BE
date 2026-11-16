package com.typeforsheet.backend.domain.music.repository;

import com.typeforsheet.backend.domain.music.entity.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicExternalIdRepository extends JpaRepository<MusicExternalId, Long> {
  Optional<MusicExternalId> findByProviderAndExternalIdTypeAndExternalId(
      MusicProvider provider, ExternalIdType externalIdType, String externalId);
}
