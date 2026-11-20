package com.typeforsheet.backend.domain.music.service;

import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicResponse;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicProvider;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MusicResolveService {
  private final MusicExternalIdRepository externalIds;
  private final MusicIdService musicIdService;
  private final MusicBrainzCatalogProvider catalog;

  public ResolveMusicResponse resolve(ResolveMusicRequest request) {
    if (request == null || !"MUSICBRAINZ".equals(request.provider())) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_MUSIC_REFERENCE);
    }
    ExternalIdType idType;
    String entity;
    if ("RELEASE_GROUP".equals(request.idType())) {
      idType = ExternalIdType.RELEASE_GROUP;
      entity = "release-group";
    } else if ("RECORDING".equals(request.idType())) {
      idType = ExternalIdType.RECORDING;
      entity = "recording";
    } else {
      throw new BusinessException(ErrorCode.UNSUPPORTED_MUSIC_REFERENCE);
    }
    String id = normalizeId(request.id());
    Long existing =
        externalIds
            .findByProviderAndExternalIdTypeAndExternalId(MusicProvider.MUSICBRAINZ, idType, id)
            .map(mapping -> mapping.getMusicItem().getId())
            .orElse(null);
    if (existing != null) {
      return new ResolveMusicResponse(existing, idType.getMusicType());
    }
    if (!catalog.exists(entity, id)) {
      throw new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND);
    }
    Long musicId =
        musicIdService.getOrCreate(idType.getMusicType(), MusicProvider.MUSICBRAINZ, idType, id);
    return new ResolveMusicResponse(musicId, idType.getMusicType());
  }

  private String normalizeId(String id) {
    if (id != null) {
      String normalized = id.toLowerCase(Locale.ROOT);
      try {
        if (normalized.length() == 36
            && UUID.fromString(normalized).toString().equals(normalized)) {
          return normalized;
        }
      } catch (IllegalArgumentException ignored) {
        // Report invalid IDs using the API error contract.
      }
    }
    throw new BusinessException(ErrorCode.INVALID_MUSIC_ID);
  }
}
