package com.typeforsheet.backend.domain.music.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveMusicRequest(
    @NotBlank String provider, @NotBlank String idType, @NotBlank String id) {}
