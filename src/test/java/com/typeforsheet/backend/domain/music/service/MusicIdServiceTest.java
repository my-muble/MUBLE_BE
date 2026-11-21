package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.entity.*;
import com.typeforsheet.backend.domain.music.repository.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@SpringBootTest
class MusicIdServiceTest {
  @Autowired MusicIdService service;
  @Autowired MusicItemRepository items;
  @Autowired MusicExternalIdRepository mappings;

  @BeforeEach
  void clean() {
    mappings.deleteAll();
    items.deleteAll();
  }

  @Test
  void createsAndReusesMappingsAndSeparatesTypes() {
    String mbid = "a1234567-1234-1234-1234-123456789abc";
    Long album =
        service.getOrCreate(
            MusicType.ALBUM, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, mbid);
    assertEquals(
        album,
        service.getOrCreate(
            MusicType.ALBUM,
            MusicProvider.MUSICBRAINZ,
            ExternalIdType.RELEASE_GROUP,
            mbid.toUpperCase(Locale.ROOT)));
    Long track =
        service.getOrCreate(
            MusicType.TRACK, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, mbid);
    Long other =
        service.getOrCreate(
            MusicType.ALBUM,
            MusicProvider.MUSICBRAINZ,
            ExternalIdType.RELEASE_GROUP,
            UUID.randomUUID().toString());
    assertNotEquals(album, track);
    assertNotEquals(album, other);
    assertEquals(MusicType.TRACK, items.findById(track).orElseThrow().getType());
    assertEquals(3, mappings.count());
    assertEquals(3, items.count());
  }

  @Test
  void rejectsInvalidTypesAndMbids() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.getOrCreate(
                MusicType.TRACK,
                MusicProvider.MUSICBRAINZ,
                ExternalIdType.RELEASE_GROUP,
                UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.getOrCreate(
                MusicType.ALBUM,
                MusicProvider.MUSICBRAINZ,
                ExternalIdType.RELEASE_GROUP,
                "invalid"));
    assertEquals(0, items.count());
  }

  @Test
  void concurrentRequestsReturnOneIdWithoutOrphans() throws Exception {
    int count = 8;
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    String mbid = UUID.randomUUID().toString();
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return service.getOrCreate(
                      MusicType.ALBUM,
                      MusicProvider.MUSICBRAINZ,
                      ExternalIdType.RELEASE_GROUP,
                      mbid);
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      Set<Long> ids = new HashSet<>();
      for (Future<Long> result : results) ids.add(result.get(30, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(1, mappings.count());
      assertEquals(1, items.count());
      assertEquals(
          ids.iterator().next(),
          service.getOrCreate(
              MusicType.ALBUM, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, mbid));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void retriesDatabaseDeadlockDuringAliasCreation() {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus status = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    MusicItemRepository itemRepository = mock(MusicItemRepository.class);
    MusicExternalIdRepository externalIdRepository = mock(MusicExternalIdRepository.class);
    MusicItem item = mock(MusicItem.class);
    when(item.getId()).thenReturn(42L);
    when(item.getType()).thenReturn(MusicType.ALBUM);
    when(itemRepository.save(any(MusicItem.class))).thenReturn(item);
    when(externalIdRepository.findByProviderAndExternalIdTypeAndExternalId(any(), any(), any()))
        .thenReturn(Optional.empty());
    AtomicInteger writes = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (writes.incrementAndGet() == 1) throw new CannotAcquireLockException("deadlock");
              return invocation.getArgument(0);
            })
        .when(externalIdRepository)
        .saveAndFlush(any(MusicExternalId.class));

    MusicIdService retryingService =
        new MusicIdService(itemRepository, externalIdRepository, transactionManager);
    assertEquals(
        42L,
        retryingService.getOrCreateWithAlias(
            MusicType.ALBUM,
            MusicProvider.MUSICBRAINZ,
            ExternalIdType.RELEASE_GROUP,
            "a1234567-1234-1234-1234-123456789abc",
            "b1234567-1234-1234-1234-123456789abc"));
    assertEquals(3, writes.get());
  }
}
