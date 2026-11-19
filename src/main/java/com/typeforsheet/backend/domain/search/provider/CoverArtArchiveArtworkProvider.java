package com.typeforsheet.backend.domain.search.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
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

    CacheEntry entry;

    synchronized (cacheLock) {
      long now = System.nanoTime();

      // 만료된 항목 제거
      cache.entrySet().removeIf(item -> now - item.getValue().expiresAtNanos() >= 0);

      // 유효한 항목은 재사용하고 없으면 다시 조회
      entry =
          cache.compute(
              normalized,
              (key, old) -> {
                if (old != null && System.nanoTime() - old.expiresAtNanos() < 0) {
                  return old;
                }

                return checkAvailability(url);
              });

      // 최대 500개 제한
      if (cache.size() > MAX_CACHE_ENTRIES) {
        String evictionKey = null;
        long earliestExpiry = Long.MAX_VALUE;

        for (var item : cache.entrySet()) {
          long expiry = item.getValue().expiresAtNanos();

          if (expiry < earliestExpiry) {
            earliestExpiry = expiry;
            evictionKey = item.getKey();
          }
        }

        if (evictionKey != null) {
          cache.remove(evictionKey);
        }
      }
    }

    return entry.available() ? url : null;
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
