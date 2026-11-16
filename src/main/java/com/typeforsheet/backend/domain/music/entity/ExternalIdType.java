package com.typeforsheet.backend.domain.music.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExternalIdType {
  RELEASE_GROUP(MusicProvider.MUSICBRAINZ, MusicType.ALBUM),
  RECORDING(MusicProvider.MUSICBRAINZ, MusicType.TRACK);

  private final MusicProvider provider;
  private final MusicType musicType;
}
