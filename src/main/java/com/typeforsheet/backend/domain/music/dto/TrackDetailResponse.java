package com.typeforsheet.backend.domain.music.dto;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;

public record TrackDetailResponse(
    Long trackId,
    ExternalReference reference,
    String title,
    String artistName,
    String artistId,
    Long durationMs,
    String albumTitle,
    String releaseGroupId,
    String coverImageUrl) {}
