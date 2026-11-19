package com.typeforsheet.backend.domain.search.provider;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.ExternalReference;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MusicBrainzCatalogProvider implements MusicCatalogProvider {

  private static final long CACHE_NANOS = TimeUnit.MINUTES.toNanos(10);

  private static final long REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

  private final HttpClient client;
  private final JsonMapper mapper;
  private final String baseUrl;
  private final String userAgent;

  private final Object requestLock = new Object();

  private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

  private long nextAllowedNanos;

  public MusicBrainzCatalogProvider(
      JsonMapper mapper,
      @Value("${app.music.musicbrainz.base-url}") String baseUrl,
      @Value("${app.music.musicbrainz.user-agent}") String userAgent) {

    this.mapper = mapper;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.userAgent = userAgent;

    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public SearchPage<Track> searchTracks(String query, int limit, int offset) {

    JsonNode root = request("recording", query, limit, offset);

    Map<String, Track> results = new LinkedHashMap<>();

    for (JsonNode item : root.path("recordings")) {

      String id = value(item, "id");
      String title = value(item, "title");

      if (id == null || title == null) {
        continue;
      }

      String artistName = artistCredit(item);
      String artistId = firstArtistId(item);

      RepresentativeReleaseSelector.SelectedRelease selected =
          RepresentativeReleaseSelector.select(item);

      String albumTitle = selected == null ? null : selected.albumTitle();

      String releaseGroupId = selected == null ? null : selected.releaseGroupId();

      Track track =
          new Track(
              reference("RECORDING", id),
              title,
              artistName,
              artistId,
              albumTitle,
              releaseGroupId,
              null,
              score(item));

      results.putIfAbsent(id, track);
    }

    return new SearchPage<>(
        new ArrayList<>(results.values()), root.path("count").asInt(0), limit, offset);
  }

  @Override
  public SearchPage<Album> searchAlbums(String query, int limit, int offset) {

    JsonNode root = request("release-group", query, limit, offset);

    Map<String, Album> results = new LinkedHashMap<>();

    for (JsonNode item : root.path("release-groups")) {

      String id = value(item, "id");
      String title = value(item, "title");

      if (id == null || title == null) {
        continue;
      }

      Album album =
          new Album(
              reference("RELEASE_GROUP", id),
              title,
              artistCredit(item),
              firstArtistId(item),
              value(item, "first-release-date"),
              value(item, "primary-type"),
              null,
              score(item));

      results.putIfAbsent(id, album);
    }

    return new SearchPage<>(
        new ArrayList<>(results.values()), root.path("count").asInt(0), limit, offset);
  }

  @Override
  public SearchPage<Artist> searchArtists(String query, int limit, int offset) {

    JsonNode root = request("artist", query, limit, offset);

    Map<String, Artist> results = new LinkedHashMap<>();

    for (JsonNode item : root.path("artists")) {

      String id = value(item, "id");
      String name = value(item, "name");

      if (id == null || name == null) {
        continue;
      }

      Artist artist =
          new Artist(
              reference("ARTIST", id),
              name,
              value(item, "type"),
              value(item, "disambiguation"),
              null,
              score(item));

      results.putIfAbsent(id, artist);
    }

    return new SearchPage<>(
        new ArrayList<>(results.values()), root.path("count").asInt(0), limit, offset);
  }

  private JsonNode request(String entity, String query, int limit, int offset) {

    String url =
        baseUrl
            + "/ws/2/"
            + entity
            + "/?query="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&fmt=json&limit="
            + limit
            + "&offset="
            + offset;

    CachedResponse cached = cache.get(url);

    if (isValid(cached)) {
      return cached.body();
    }

    synchronized (requestLock) {
      cached = cache.get(url);

      if (isValid(cached)) {
        return cached.body();
      }

      waitForRequestSlot();

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(8))
              .header("User-Agent", userAgent)
              .header("Accept", "application/json")
              .GET()
              .build();

      try {
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 400) {
          throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (response.statusCode() != 200) {
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
        }

        JsonNode body = mapper.readTree(response.body());

        if (cache.size() >= 500) {
          cache.clear();
        }

        cache.put(url, new CachedResponse(body, System.nanoTime() + CACHE_NANOS));

        return body;

      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();

        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);

      } catch (BusinessException exception) {
        throw exception;

      } catch (Exception exception) {
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
      }
    }
  }

  private void waitForRequestSlot() {

    long remaining = nextAllowedNanos - System.nanoTime();

    if (remaining > 0) {
      try {
        TimeUnit.NANOSECONDS.sleep(remaining);

      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();

        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
      }
    }

    nextAllowedNanos = System.nanoTime() + REQUEST_INTERVAL_NANOS;
  }

  private boolean isValid(CachedResponse cached) {
    return cached != null && System.nanoTime() - cached.expiresAtNanos() < 0;
  }

  private ExternalReference reference(String type, String id) {

    return new ExternalReference("MUSICBRAINZ", type, id);
  }

  private String value(JsonNode node, String field) {

    JsonNode child = node.path(field);

    if (child.isMissingNode() || child.isNull()) {
      return null;
    }

    String result = child.asText();

    return result.isBlank() ? null : result;
  }

  private int score(JsonNode node) {
    return node.path("score").asInt(0);
  }

  private String firstArtistId(JsonNode node) {

    for (JsonNode credit : node.path("artist-credit")) {

      String id = value(credit.path("artist"), "id");

      if (id != null) {
        return id;
      }
    }

    return null;
  }

  private String artistCredit(JsonNode node) {

    StringBuilder builder = new StringBuilder();

    for (JsonNode credit : node.path("artist-credit")) {

      if (credit.isTextual()) {
        builder.append(credit.asText());

      } else if (credit.isObject()) {

        String name = value(credit, "name");

        if (name == null) {
          name = value(credit.path("artist"), "name");
        }

        if (name != null) {
          builder.append(name);
        }
      }
    }

    String result = builder.toString().trim();

    return result.isEmpty() ? null : result;
  }

  private record CachedResponse(JsonNode body, long expiresAtNanos) {}
}
