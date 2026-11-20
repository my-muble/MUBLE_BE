package com.typeforsheet.backend.domain.search.service;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.dto.response.UnifiedMusicSearchResponse;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class MusicSearchService {

  private static final int ARTWORK_THREADS = 6;
  private static final int ARTWORK_QUEUE_CAPACITY = 48;
  private static final long ARTWORK_TIMEOUT_SECONDS = 4;

  private final MusicCatalogProvider catalogProvider;
  private final AlbumArtworkProvider artworkProvider;

  private final ThreadPoolExecutor artworkExecutor =
      new ThreadPoolExecutor(
          ARTWORK_THREADS,
          ARTWORK_THREADS,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(ARTWORK_QUEUE_CAPACITY),
          new ThreadPoolExecutor.AbortPolicy());

  public MusicSearchService(
      MusicCatalogProvider catalogProvider, AlbumArtworkProvider artworkProvider) {

    this.catalogProvider = catalogProvider;
    this.artworkProvider = artworkProvider;
  }

  // 통합 검색
  public UnifiedMusicSearchResponse searchAll(String query, int limit, int offset) {

    String normalizedQuery = validate(query, limit, offset);

    Map<String, String> errors = new HashMap<>();

    SearchPage<Track> tracks =
        searchSafely("tracks", () -> searchTracks(normalizedQuery, limit, offset), errors);

    SearchPage<Album> albums =
        searchSafely("albums", () -> searchAlbums(normalizedQuery, limit, offset), errors);

    SearchPage<Artist> artists =
        searchSafely("artists", () -> searchArtists(normalizedQuery, limit, offset), errors);

    // 모든 유형의 조회가 실패한 경우
    if (errors.size() == 3) {
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    }

    return new UnifiedMusicSearchResponse(tracks, albums, artists, errors);
  }

  // 곡 검색
  public SearchPage<Track> searchTracks(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Track> page = catalogProvider.searchTracks(keyword, limit, offset);

    return enrichArtwork(page, Track::releaseGroupId, Track::withCover);
  }

  // 앨범 검색
  public SearchPage<Album> searchAlbums(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Album> page = catalogProvider.searchAlbums(keyword, limit, offset);

    return enrichArtwork(page, album -> album.reference().id(), Album::withCover);
  }

  // 아티스트 검색
  public SearchPage<Artist> searchArtists(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    return catalogProvider.searchArtists(keyword, limit, offset);
  }

  // 유형별 외부 API 조회 실패 처리
  private <T> SearchPage<T> searchSafely(
      String type, Supplier<SearchPage<T>> search, Map<String, String> errors) {

    try {
      return search.get();

    } catch (BusinessException exception) {

      // 외부 음악 검색 API 오류만 부분 실패로 처리
      if (exception.getErrorCode() != ErrorCode.MUSIC_CATALOG_UNAVAILABLE) {
        throw exception;
      }

      errors.put(type, ErrorCode.MUSIC_CATALOG_UNAVAILABLE.name());

      return null;
    }
  }

  // 커버 이미지 병렬 조회
  private <T> SearchPage<T> enrichArtwork(
      SearchPage<T> page, Function<T, String> idExtractor, BiFunction<T, String, T> withCover) {

    List<T> originals = page.results();
    List<Future<String>> tasks = new ArrayList<>(originals.size());

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ARTWORK_TIMEOUT_SECONDS);

    for (T item : originals) {

      String releaseGroupId = idExtractor.apply(item);

      if (releaseGroupId == null) {
        tasks.add(null);
        continue;
      }

      try {
        tasks.add(artworkExecutor.submit(() -> artworkProvider.findCoverUrl(releaseGroupId)));

      } catch (RejectedExecutionException exception) {
        tasks.add(null);
      }
    }

    List<T> results = new ArrayList<>(originals.size());
    boolean interrupted = false;

    try {
      for (int i = 0; i < originals.size(); i++) {

        Future<String> task = tasks.get(i);
        String coverUrl = null;

        if (!interrupted && task != null) {

          try {
            if (task.isDone()) {

              // 이미 완료된 작업은 기한이 지나도 결과 회수
              coverUrl = task.get();

            } else {

              // 미완료 작업에만 남은 시간 적용
              long remaining = deadline - System.nanoTime();

              if (remaining > 0) {
                coverUrl = task.get(remaining, TimeUnit.NANOSECONDS);

              } else if (task.isDone()) {

                // 기한 확인 중 작업이 완료된 경우에도 결과 유지
                coverUrl = task.get();
              }
            }

          } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();
            interrupted = true;

          } catch (ExecutionException | TimeoutException | CancellationException exception) {

            coverUrl = null;
          }
        }

        // 기존 검색 결과 순서 유지
        results.add(withCover.apply(originals.get(i), coverUrl));
      }

    } finally {

      // 미완료 작업 취소
      for (Future<String> task : tasks) {
        if (task != null && !task.isDone()) {
          task.cancel(true);
        }
      }

      // 취소된 작업을 대기열에서 제거
      artworkExecutor.purge();
    }

    return new SearchPage<>(results, page.total(), page.limit(), page.offset());
  }

  // 검색어 및 페이지네이션 검증
  private String validate(String query, int limit, int offset) {

    if (query == null) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    String normalized = query.strip();

    if (normalized.isBlank()
        || normalized.codePointCount(0, normalized.length()) > 100
        || limit < 1
        || limit > 20
        || offset < 0
        || offset > 10000) {

      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return normalized;
  }

  @PreDestroy
  public void shutdown() {
    artworkExecutor.shutdownNow();
  }
}
