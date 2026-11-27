package com.typeforsheet.backend.domain.music.service;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
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
public class AlbumDetailService {
  private final MusicItemRepository items;
  private final MusicExternalIdRepository externalIds;
  private final MusicBrainzCatalogProvider catalog;
  private final AlbumArtworkProvider artwork;

  public AlbumDetailResponse getAlbum(Long albumId) {
    if (albumId == null || albumId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    MusicItem item =
        items
            .findById(albumId)
            .filter(value -> value.getType() == MusicType.ALBUM)
            .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
    String mbid =
        externalIds
            .findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
                item.getId(), MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP)
            .map(MusicExternalId::getExternalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MUSIC_MAPPING_MISSING));
    var album = catalog.getAlbumDetail(mbid);
    String cover = null;
    try {
      cover = artwork.findCoverUrl(album.releaseGroupId());
    } catch (RuntimeException ignored) {
      // Artwork is optional and must not fail the album lookup.
    }
    return new AlbumDetailResponse(
        albumId,
        new ExternalReference("MUSICBRAINZ", "RELEASE_GROUP", album.releaseGroupId()),
        album.title(),
        album.artistName(),
        album.artistId(),
        album.releaseDate(),
        album.releaseType(),
        cover,
        album.tracks(),
        album.tracksComplete() ? album.tracks().size() : null,
        totalDuration(album));
  }

  private Long totalDuration(MusicBrainzCatalogProvider.AlbumDetail album) {
    if (!album.tracksComplete() || album.tracks().isEmpty()) return null;
    long total = 0;
    for (var track : album.tracks()) {
      Long duration = track.durationMs();
      if (duration == null || duration < 0) return null;
      try {
        total = Math.addExact(total, duration);
      } catch (ArithmeticException overflow) {
        return null;
      }
    }
    return total;
  }
}
