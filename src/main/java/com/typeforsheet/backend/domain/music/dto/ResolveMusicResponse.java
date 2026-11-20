package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.music.entity.MusicType;

public record ResolveMusicResponse(Long musicId, MusicType type) {}
