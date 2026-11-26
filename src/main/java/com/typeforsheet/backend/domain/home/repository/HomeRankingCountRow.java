package com.typeforsheet.backend.domain.home.repository;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import java.time.LocalDateTime;

public interface HomeRankingCountRow {

  Long getMusicId();

  MusicType getMusicType();

  Long getRecordCount();

  Long getRatedRecordCount();

  LocalDateTime getLatestCreatedAt();
}
