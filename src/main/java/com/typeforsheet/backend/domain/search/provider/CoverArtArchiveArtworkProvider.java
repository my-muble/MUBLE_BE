package com.typeforsheet.backend.domain.search.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CoverArtArchiveArtworkProvider implements AlbumArtworkProvider {

  private static final long SUCCESS_CACHE_NANOS = TimeUnit.HOURS.toNanos(6);

  private static final long FAILURE_CACHE_NANOS = TimeUnit.MINUTES.toNanos(1);

  private static final int MAX_CACHE_ENTRIES = 500;

  private final Object cacheLock = new Object();

  private final ConcurrentHashMap<String, CompletableFuture<CacheEntry>> inFlight =
      new ConcurrentHashMap<>();

  private final HttpClient client;
  private final boolean enabled;
  private final String baseUrl;

  private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public CoverArtArchiveArtworkProvider(
      @Value("${app.music.artwork.enabled:false}") boolean enabled,
      @Value("${app.music.artwork.caa-base-url}") String baseUrl) {

    this.enabled = enabled;
    this.baseUrl = baseUrl.replaceAll("/+$", "");

    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public String findCoverUrl(String releaseGroupId) {

    if (!enabled || releaseGroupId == null) {
      return null;
    }

    String normalized = releaseGroupId.toLowerCase(Locale.ROOT);

    if (!isValidMbid(normalized)) {
      return null;
    }

    String url = baseUrl + "/release-group/" + normalized + "/front-250";

    CacheEntry entry = getValidCached(normalized);

    if (entry == null) {
      CompletableFuture<CacheEntry> newTask = new CompletableFuture<>();

      CompletableFuture<CacheEntry> existingTask = inFlight.putIfAbsent(normalized, newTask);

      if (existingTask == null) {
        try {
          // 다른 요청이 이미 캐시를 채웠을 수 있으므로 재확인
          entry = getValidCached(normalized);

          if (entry == null) {
            // HTTP 조회는 전역 잠금 밖에서 실행
            entry = checkAvailability(url);
            putCached(normalized, entry);
          }

          newTask.complete(entry);

        } catch (RuntimeException | Error exception) {
          newTask.completeExceptionally(exception);
          throw exception;

        } finally {
          inFlight.remove(normalized, newTask);
        }

      } else {
        // 동일 ID를 조회 중인 요청의 결과만 기다림
        try {
          entry = existingTask.join();

        } catch (CompletionException exception) {
          if (exception.getCause() instanceof RuntimeException cause) {
            throw cause;
          }

          if (exception.getCause() instanceof Error cause) {
            throw cause;
          }

          throw exception;
        }
      }
    }

    return entry.available() ? url : null;
  }

  private CacheEntry getValidCached(String key) {
    CacheEntry entry = cache.get(key);

    if (entry == null) {
      return null;
    }

    if (System.nanoTime() - entry.expiresAtNanos() >= 0) {
      // 만료된 값만 조건부 제거해 새 캐시 값을 지우지 않음
      cache.remove(key, entry);
      return null;
    }

    return entry;
  }

  private void putCached(String key, CacheEntry entry) {
    synchronized (cacheLock) {
      long now = System.nanoTime();

      // 기존 TTL 유지: 만료된 캐시 제거
      cache.entrySet().removeIf(item -> now - item.getValue().expiresAtNanos() >= 0);

      cache.put(key, entry);

      // 최대 500개 유지
      // 초과 시 만료 예정 시각이 가장 빠른 항목부터 제거
      while (cache.size() > MAX_CACHE_ENTRIES) {
        String evictionKey =
            cache.entrySet().stream()
                .min(Comparator.comparingLong(item -> item.getValue().expiresAtNanos()))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (evictionKey == null) {
          break;
        }

        cache.remove(evictionKey);
      }
    }
  }

  private CacheEntry checkAvailability(String url) {

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();

    try {
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

      int status = response.statusCode();

      boolean available =
          status == 200
              || status == 301
              || status == 302
              || status == 303
              || status == 307
              || status == 308;

      long ttl = available || status == 404 ? SUCCESS_CACHE_NANOS : FAILURE_CACHE_NANOS;

      return new CacheEntry(available, System.nanoTime() + ttl);

    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return unavailable();

    } catch (Exception exception) {
      return unavailable();
    }
  }

  private CacheEntry unavailable() {
    return new CacheEntry(false, System.nanoTime() + FAILURE_CACHE_NANOS);
  }

  private boolean isValidMbid(String value) {
    try {
      return UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private record CacheEntry(boolean available, long expiresAtNanos) {}
}
