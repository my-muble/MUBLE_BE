package com.typeforsheet.backend.domain.music.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MusicResolveServiceTest {
  @Autowired MusicResolveService service;
  @Autowired MusicExternalIdRepository mappings;
  @Autowired MusicItemRepository items;
  @MockitoBean MusicBrainzCatalogProvider catalog;
  private static final String ID = "a1234567-1234-1234-1234-123456789abc";

  @BeforeEach
  void clean() {
    mappings.deleteAll();
    items.deleteAll();
  }

  @Test
  void createsThenReusesMappingWithoutAnotherLookup() {
    when(catalog.exists("release-group", ID)).thenReturn(true);
    var first = service.resolve(new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", ID));
    var second =
        service.resolve(
            new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", ID.toUpperCase(Locale.ROOT)));
    assertEquals(first, second);
    assertEquals(MusicType.ALBUM, first.type());
    assertEquals(1, mappings.count());
    verify(catalog, times(1)).exists("release-group", ID);
  }

  @Test
  void createsTrackMapping() {
    when(catalog.exists("recording", ID)).thenReturn(true);
    assertEquals(
        MusicType.TRACK,
        service.resolve(new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", ID)).type());
    assertEquals(1, items.count());
  }

  @Test
  void rejectsBadInputMissingMusicAndUpstreamFailureWithoutSaving() {
    assertError(
        ErrorCode.INVALID_MUSIC_ID, new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", "bad"));
    assertError(
        ErrorCode.UNSUPPORTED_MUSIC_REFERENCE, new ResolveMusicRequest("OTHER", "RECORDING", ID));
    assertError(
        ErrorCode.UNSUPPORTED_MUSIC_REFERENCE,
        new ResolveMusicRequest("MUSICBRAINZ", "ARTIST", ID));
    when(catalog.exists("recording", ID))
        .thenReturn(false)
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));
    assertError(
        ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND,
        new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", ID));
    assertError(
        ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
        new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", ID));
    assertEquals(0, items.count());
    assertEquals(0, mappings.count());
  }

  @Test
  void concurrentResolveCreatesOneMapping() throws Exception {
    when(catalog.exists("recording", ID)).thenReturn(true);
    int count = 8;
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Long>> futures = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return service
                      .resolve(new ResolveMusicRequest("MUSICBRAINZ", "RECORDING", ID))
                      .musicId();
                }));
      }
      start.countDown();
      Set<Long> ids = new HashSet<>();
      for (Future<Long> future : futures) ids.add(future.get(30, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(1, items.count());
      assertEquals(1, mappings.count());
    } finally {
      executor.shutdownNow();
    }
  }

  private void assertError(ErrorCode code, ResolveMusicRequest request) {
    assertEquals(
        code, assertThrows(BusinessException.class, () -> service.resolve(request)).getErrorCode());
  }
}
