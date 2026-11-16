package com.typeforsheet.backend.domain.music.repository;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicItemRepository extends JpaRepository<MusicItem, Long> {}
