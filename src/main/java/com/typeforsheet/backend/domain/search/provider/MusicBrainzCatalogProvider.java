package com.typeforsheet.backend.domain.search.provider;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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

  private final ReentrantLock requestLock = new ReentrantLock();

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

    return requestUrl(url, false);
  }

  public boolean exists(String entity, String id) {
    if (!"recording".equals(entity) && !"release-group".equals(entity)) {
      throw new IllegalArgumentException("Unsupported MusicBrainz entity");
    }
    JsonNode body = requestUrl(baseUrl + "/ws/2/" + entity + "/" + id + "?fmt=json", true);
    if (body == null) {
      return false;
    }
    String returnedId = value(body, "id");
    try {
      if (returnedId == null
          || !java.util.UUID.fromString(returnedId).toString().equals(returnedId)) {
        throw new IllegalArgumentException("Invalid MusicBrainz response ID");
      }
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
    }
    return true;
  }

  public record AlbumDetail(
      String releaseGroupId,
      String title,
      String artistName,
      String artistId,
      String releaseDate,
      String releaseType,
      List<AlbumTrack> tracks) {}

  public record TrackDetail(
      String recordingId,
      String title,
      String artistName,
      String artistId,
      Long durationMs,
      String albumTitle,
      String releaseGroupId) {}

  public TrackDetail getTrackDetail(String id) {
    JsonNode recording =
        requestUrl(baseUrl + "/ws/2/recording/" + id + "?fmt=json&inc=artist-credits", true);
    if (recording == null) throw new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND);
    String recordingId = value(recording, "id");
    String title = value(recording, "title");
    if (!validUuid(recordingId) || title == null)
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);

    List<JsonNode> releases = new ArrayList<>();
    int offset = 0;
    while (true) {
      JsonNode page =
          requestUrl(
              baseUrl
                  + "/ws/2/release?recording="
                  + recordingId
                  + "&fmt=json&inc=artist-credits+release-groups&limit=100&offset="
                  + offset,
              false,
              false);
      JsonNode entries = page.path("releases");
      int count = page.path("release-count").asInt(-1);
      if (!entries.isArray() || count < 0)
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      int size = 0;
      for (JsonNode release : entries) {
        if (validUuid(value(release, "id"))
            && validUuid(value(release.path("release-group"), "id"))) releases.add(release);
        size++;
      }
      offset += size;
      if (offset >= count) break;
      if (size == 0) throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    }
    releases.sort(Comparator.comparing(release -> value(release, "id")));
    var selected = RepresentativeReleaseSelector.select(recording, releases);
    JsonNode length = recording.path("length");
    Long duration = length.isIntegralNumber() ? length.asLong() : null;
    return new TrackDetail(
        recordingId,
        title,
        artistCredit(recording),
        firstArtistId(recording),
        duration,
        selected == null ? null : selected.albumTitle(),
        selected == null ? null : selected.releaseGroupId());
  }

  public AlbumDetail getAlbumDetail(String id) {
    JsonNode group =
        requestUrl(baseUrl + "/ws/2/release-group/" + id + "?fmt=json&inc=artist-credits", true);
    if (group == null) throw new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND);
    String groupId = value(group, "id");
    String title = value(group, "title");
    if (title == null || !validUuid(groupId))
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    List<JsonNode> releases = new ArrayList<>();
    int offset = 0;
    while (true) {
      JsonNode page =
          requestUrl(
              baseUrl
                  + "/ws/2/release?release-group="
                  + groupId
                  + "&fmt=json&inc=artist-credits&limit=100&offset="
                  + offset,
              false,
              false);
      JsonNode entries = page.path("releases");
      int count = page.path("release-count").asInt(-1);
      if (!entries.isArray() || count < 0)
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      int size = 0;
      for (JsonNode release : entries) {
        releases.add(release);
        size++;
      }
      offset += size;
      if (offset >= count) break;
      if (size == 0) throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    }
    Comparator<JsonNode> order =
        Comparator.comparingInt(
                (JsonNode release) -> "Official".equalsIgnoreCase(value(release, "status")) ? 0 : 1)
            .thenComparing(
                release -> value(release, "date"), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(release -> value(release, "id"));
    JsonNode selected =
        releases.stream()
            .filter(release -> validUuid(value(release, "id")))
            .min(order)
            .orElse(null);
    if (!releases.isEmpty() && selected == null)
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    List<AlbumTrack> tracks = new ArrayList<>();
    if (selected != null) {
      String releaseId = value(selected, "id");
      JsonNode release =
          requestUrl(
              baseUrl
                  + "/ws/2/release/"
                  + releaseId
                  + "?fmt=json&inc=recordings+artist-credits+media",
              true);
      if (release == null || !release.path("media").isArray())
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      int discIndex = 0;
      for (JsonNode medium : release.path("media")) {
        int discNumber = medium.path("position").asInt(++discIndex);
        if (!medium.path("tracks").isArray())
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
        for (JsonNode track : medium.path("tracks")) {
          JsonNode recording = track.path("recording");
          String trackTitle = value(track, "title");
          if (trackTitle == null) trackTitle = value(recording, "title");
          String trackArtist = artistCredit(track);
          if (trackArtist == null) trackArtist = artistCredit(recording);
          if (trackArtist == null) trackArtist = artistCredit(release);
          if (trackArtist == null) trackArtist = artistCredit(group);
          JsonNode length = track.path("length");
          if (!length.isIntegralNumber()) length = recording.path("length");
          Long duration = length.isIntegralNumber() ? length.asLong() : null;
          tracks.add(
              new AlbumTrack(
                  discNumber,
                  value(track, "number"),
                  trackTitle,
                  trackArtist,
                  duration,
                  value(recording, "id")));
        }
      }
    }
    return new AlbumDetail(
        groupId,
        title,
        artistCredit(group),
        firstArtistId(group),
        value(group, "first-release-date"),
        value(group, "primary-type"),
        List.copyOf(tracks));
  }

  private boolean validUuid(String value) {
    try {
      return value != null && java.util.UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private JsonNode requestUrl(String url, boolean lookup) {
    return requestUrl(url, lookup, !lookup);
  }

  private JsonNode requestUrl(String url, boolean lookup, boolean search) {

    CachedResponse cached = cache.get(url);

    if (isValid(cached)) {
      return cached.body();
    }

    boolean acquired;

    try {
      acquired = requestLock.tryLock(2, TimeUnit.SECONDS);

    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();

      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
    }

    if (!acquired) {
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
    }

    try {
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

        if (lookup && response.statusCode() == 404) {
          return null;
        }

        if (search && response.statusCode() == 400) {
          throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (response.statusCode() != 200) {
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
        }

        JsonNode body = mapper.readTree(response.body());

        if (lookup) {
          String returnedId = value(body, "id");
          String path = response.uri().getPath();
          if (returnedId == null || !path.endsWith("/" + returnedId)) {
            throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
          }
        }

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

    } finally {
      requestLock.unlock();
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

        JsonNode joinphrase = credit.path("joinphrase");

        if (joinphrase.isTextual()) {
          builder.append(joinphrase.asText());
        }
      }
    }

    String result = builder.toString().trim();

    return result.isEmpty() ? null : result;
  }

  private record CachedResponse(JsonNode body, long expiresAtNanos) {}
}
