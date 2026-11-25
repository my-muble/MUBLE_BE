package com.typeforsheet.backend.domain.user.repository;

import com.typeforsheet.backend.domain.music.entity.MusicType;

public record RecordStatsRow(MusicType musicType, Byte ratingHalfSteps, Long recordCount) {}
