package com.typeforsheet.backend.domain.music.service;

import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MusicIdService {
  private final MusicItemRepository musicItems;
  private final MusicExternalIdRepository externalIds;
  private final TransactionTemplate transaction;

  public MusicIdService(
      MusicItemRepository musicItems,
      MusicExternalIdRepository externalIds,
      PlatformTransactionManager transactionManager) {
    this.musicItems = musicItems;
    this.externalIds = externalIds;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Long getOrCreate(
      MusicType type, MusicProvider provider, ExternalIdType idType, String externalId) {
    if (type == null
        || provider == null
        || idType == null
        || idType.getProvider() != provider
        || idType.getMusicType() != type
        || externalId == null) {
      throw new IllegalArgumentException("Invalid music external ID mapping");
    }
    String normalized = normalize(provider, externalId);
    try {
      return transaction.execute(
          status ->
              externalIds
                  .findByProviderAndExternalIdTypeAndExternalId(provider, idType, normalized)
                  .map(mapping -> mapping.getMusicItem().getId())
                  .orElseGet(
                      () -> {
                        MusicItem item = musicItems.save(new MusicItem(type));
                        externalIds.saveAndFlush(
                            new MusicExternalId(item, provider, idType, normalized));
                        return item.getId();
                      }));
    } catch (DataIntegrityViolationException conflict) {
      Long existing =
          transaction.execute(
              status ->
                  externalIds
                      .findByProviderAndExternalIdTypeAndExternalId(provider, idType, normalized)
                      .map(mapping -> mapping.getMusicItem().getId())
                      .orElse(null));
      if (existing == null) throw conflict;
      return existing;
    }
  }

  public Long getOrCreateWithAlias(
      MusicType type,
      MusicProvider provider,
      ExternalIdType idType,
      String externalId,
      String canonicalId) {
    if (type == null
        || provider == null
        || idType == null
        || idType.getProvider() != provider
        || idType.getMusicType() != type
        || externalId == null
        || canonicalId == null) {
      throw new IllegalArgumentException("Invalid music external ID mapping");
    }
    String alias = normalize(provider, externalId);
    String canonical = normalize(provider, canonicalId);
    DataAccessException lastConflict = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return transaction.execute(
            status -> {
              MusicExternalId canonicalMapping =
                  externalIds
                      .findByProviderAndExternalIdTypeAndExternalId(provider, idType, canonical)
                      .orElse(null);
              MusicExternalId aliasMapping =
                  alias.equals(canonical)
                      ? canonicalMapping
                      : externalIds
                          .findByProviderAndExternalIdTypeAndExternalId(provider, idType, alias)
                          .orElse(null);
              if (canonicalMapping != null
                  && aliasMapping != null
                  && !canonicalMapping
                      .getMusicItem()
                      .getId()
                      .equals(aliasMapping.getMusicItem().getId())) {
                throw new BusinessException(ErrorCode.MUSIC_MAPPING_CONFLICT);
              }
              MusicItem item =
                  canonicalMapping != null
                      ? canonicalMapping.getMusicItem()
                      : aliasMapping != null
                          ? aliasMapping.getMusicItem()
                          : musicItems.save(new MusicItem(type));
              if (canonicalMapping == null) {
                externalIds.saveAndFlush(new MusicExternalId(item, provider, idType, canonical));
              }
              if (!alias.equals(canonical) && aliasMapping == null) {
                externalIds.saveAndFlush(new MusicExternalId(item, provider, idType, alias));
              }
              return item.getId();
            });
      } catch (DataIntegrityViolationException | PessimisticLockingFailureException conflict) {
        lastConflict = conflict;
      }
    }
    throw lastConflict;
  }

  private String normalize(MusicProvider provider, String externalId) {
    if (provider == MusicProvider.MUSICBRAINZ) {
      String value = externalId.toLowerCase(Locale.ROOT);
      try {
        if (value.length() == 36 && UUID.fromString(value).toString().equals(value)) return value;
      } catch (IllegalArgumentException ignored) {
        // Reject malformed MBIDs below.
      }
      throw new IllegalArgumentException("Invalid MusicBrainz MBID");
    }
    if (externalId.isBlank() || externalId.length() > 255)
      throw new IllegalArgumentException("Invalid external ID");
    return externalId;
  }
}
