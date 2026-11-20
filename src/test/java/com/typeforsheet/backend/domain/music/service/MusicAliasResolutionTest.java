package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.global.exception.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MusicAliasResolutionTest {
  private static final String OLD = "a1234567-1234-1234-1234-123456789abc";
  private static final String CANONICAL = "b1234567-1234-1234-1234-123456789abc";
  @Autowired MusicResolveService resolve;
  @Autowired MusicIdService musicIds;
  @Autowired MusicExternalIdRepository mappings;
  @Autowired MusicItemRepository items;
  @MockitoBean MusicBrainzCatalogProvider catalog;

  @BeforeEach
  void clean() {
    mappings.deleteAll();
    items.deleteAll();
    when(catalog.resolveCanonicalId("recording", OLD)).thenReturn(CANONICAL);
    when(catalog.resolveCanonicalId("recording", CANONICAL)).thenReturn(CANONICAL);
  }

  @Test
  void createsOneItemForNewAliasAndCanonical() {
    Long first = resolve(OLD);
    assertEquals(first, resolve(CANONICAL));
    assertEquals(first, resolve(OLD));
    assertEquals(1, items.count());
    assertEquals(2, mappings.count());
  }

  @Test
  void reusesExistingAliasWithoutContactingCatalog() {
    Long existing = create(OLD);
    when(catalog.resolveCanonicalId("recording", OLD))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    assertEquals(existing, resolve(OLD));
    assertEquals(1, items.count());
    assertEquals(1, mappings.count());
    verifyNoInteractions(catalog);
  }

  @Test
  void reusesExistingCanonicalAndAddsAlias() {
    Long existing = create(CANONICAL);
    assertEquals(existing, resolve(OLD));
    assertEquals(1, items.count());
    assertEquals(2, mappings.count());
  }

  @Test
  void reportsExistingConflictWithoutChangingEitherItem() {
    Long oldId = create(OLD);
    Long canonicalId = create(CANONICAL);
    assertNotEquals(oldId, canonicalId);
    var failure =
        assertThrows(
            BusinessException.class,
            () ->
                musicIds.getOrCreateWithAlias(
                    MusicType.TRACK,
                    MusicProvider.MUSICBRAINZ,
                    ExternalIdType.RECORDING,
                    OLD,
                    CANONICAL));
    assertEquals(ErrorCode.MUSIC_MAPPING_CONFLICT, failure.getErrorCode());
    assertEquals(2, items.count());
    assertEquals(2, mappings.count());
    assertEquals(
        oldId,
        mappings
            .findByProviderAndExternalIdTypeAndExternalId(
                MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, OLD)
            .orElseThrow()
            .getMusicItem()
            .getId());
    assertEquals(
        canonicalId,
        mappings
            .findByProviderAndExternalIdTypeAndExternalId(
                MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, CANONICAL)
            .orElseThrow()
            .getMusicItem()
            .getId());
    assertEquals(oldId, resolve(OLD));
    assertEquals(canonicalId, resolve(CANONICAL));
    verifyNoInteractions(catalog);
  }

  @Test
  void reportsConflictDiscoveredDuringNewAliasResolution() {
    Long canonicalId = create(CANONICAL);
    when(catalog.resolveCanonicalId("recording", OLD))
        .thenAnswer(
            invocation -> {
              create(OLD);
              return CANONICAL;
            });
    var failure = assertThrows(BusinessException.class, () -> resolve(OLD));
    assertEquals(ErrorCode.MUSIC_MAPPING_CONFLICT, failure.getErrorCode());
    assertEquals(
        canonicalId,
        mappings
            .findByProviderAndExternalIdTypeAndExternalId(
                MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, CANONICAL)
            .orElseThrow()
            .getMusicItem()
            .getId());
    assertEquals(2, items.count());
    assertEquals(2, mappings.count());
  }

  @Test
  void concurrentlyResolvingNewAliasAndCanonicalConverges() throws Exception {
    int count = 8;
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Long>> futures = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        String id = i % 2 == 0 ? OLD : CANONICAL;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return resolve(id);
                }));
      }
      start.countDown();
      Set<Long> result = new HashSet<>();
      for (Future<Long> future : futures) result.add(future.get(30, TimeUnit.SECONDS));
      assertEquals(1, result.size());
      assertEquals(1, items.count());
      assertEquals(2, mappings.count());
    } finally {
      executor.shutdownNow();
    }
  }

  private Long resolve(String id) {
    return resolve.resolve(new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", id)).musicId();
  }

  private Long create(String id) {
    return musicIds.getOrCreate(
        MusicType.TRACK, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, id);
  }
}
