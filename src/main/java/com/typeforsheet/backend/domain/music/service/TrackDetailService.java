package com.typeforsheet.backend.domain.music.service;

import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackDetailService {
  private final MusicItemRepository items;
  private final MusicExternalIdRepository externalIds;
  private final MusicBrainzCatalogProvider catalog;
  private final AlbumArtworkProvider artwork;

  public TrackDetailResponse getTrack(Long trackId) {
    if (trackId == null || trackId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    MusicItem item =
        items
            .findById(trackId)
            .filter(value -> value.getType() == MusicType.TRACK)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
    String mbid =
        externalIds
            .findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
                item.getId(), MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING)
            .map(MusicExternalId::getExternalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MUSIC_MAPPING_MISSING));
    var track = catalog.getTrackDetail(mbid);
    String cover = null;
    if (track.releaseGroupId() != null) {
      try {
        cover = artwork.findCoverUrl(track.releaseGroupId());
      } catch (RuntimeException ignored) {
        // Artwork is optional and must not fail the track lookup.
      }
    }
    return new TrackDetailResponse(
        trackId,
        new ExternalReference("MUSICBRAINZ", "RECORDING", track.recordingId()),
        track.title(),
        track.artistName(),
        track.artistId(),
        track.durationMs(),
        track.albumTitle(),
        track.releaseGroupId(),
        cover,
        track.releaseDate());
  }
}
