package com.typeforsheet.backend.domain.search.service;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
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

  public SearchPage<Track> searchTracks(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Track> page = catalogProvider.searchTracks(keyword, limit, offset);

    return enrichArtwork(page, Track::releaseGroupId, Track::withCover);
  }

  public SearchPage<Album> searchAlbums(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Album> page = catalogProvider.searchAlbums(keyword, limit, offset);

    return enrichArtwork(page, album -> album.reference().id(), Album::withCover);
  }

  public SearchPage<Artist> searchArtists(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    return catalogProvider.searchArtists(keyword, limit, offset);
  }

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
          long remaining = deadline - System.nanoTime();

          if (remaining > 0) {
            try {
              coverUrl = task.get(remaining, TimeUnit.NANOSECONDS);

            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              interrupted = true;

            } catch (ExecutionException | TimeoutException | CancellationException exception) {
              coverUrl = null;
            }
          }
        }

        results.add(withCover.apply(originals.get(i), coverUrl));
      }

    } finally {
      for (Future<String> task : tasks) {
        if (task != null && !task.isDone()) {
          task.cancel(true);
        }
      }

      artworkExecutor.purge();
    }

    return new SearchPage<>(results, page.total(), page.limit(), page.offset());
  }

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
