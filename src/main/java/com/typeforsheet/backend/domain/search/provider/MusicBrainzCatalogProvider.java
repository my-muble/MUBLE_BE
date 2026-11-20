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
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MusicBrainzCatalogProvider implements MusicCatalogProvider {

  private static final Logger log = LoggerFactory.getLogger(MusicBrainzCatalogProvider.class);

  private static final long CACHE_NANOS = TimeUnit.MINUTES.toNanos(10);

  private static final long REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

  private static final long REQUEST_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(10);

  private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(3);

  private final HttpClient client;
  private final JsonMapper mapper;
  private final String baseUrl;
  private final String userAgent;

  private final ReentrantLock requestLock = new ReentrantLock();

  private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

  private final Map<String, Long> browseCacheExpirations = new ConcurrentHashMap<>();

  private long nextAllowedNanos;

  private Instant serverCooldownUntil = Instant.EPOCH;

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
            .followRedirects(HttpClient.Redirect.NEVER)
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
    return resolveCanonicalId(entity, id) != null;
  }

  public String resolveCanonicalId(String entity, String id) {
    if (!"recording".equals(entity) && !"release-group".equals(entity)) {
      throw new IllegalArgumentException("Unsupported MusicBrainz entity");
    }
    JsonNode body =
        requestUrl(
            baseUrl + "/ws/2/" + entity + "/" + id + "?fmt=json",
            true,
            response -> validUuid(value(response, "id")));
    if (body == null) {
      return null;
    }
    String returnedId = value(body, "id");
    try {
      if (returnedId == null
          || !java.util.UUID.fromString(returnedId).toString().equals(returnedId)) {
        throw new IllegalArgumentException("Invalid MusicBrainz response ID");
      }
    } catch (IllegalArgumentException exception) {
      log.warn(
          "MusicBrainz failure reason=invalid_lookup_id operation=exists http_status=200 exception_type={}",
          exception.getClass().getSimpleName());
      throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
    }
    return returnedId;
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
      String releaseGroupId,
      String releaseDate) {}

  public TrackDetail getTrackDetail(String id) {
    JsonNode recording =
        requestUrl(
            baseUrl + "/ws/2/recording/" + id + "?fmt=json&inc=artist-credits",
            true,
            response -> validUuid(value(response, "id")) && value(response, "title") != null);
    if (recording == null) throw new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND);
    String recordingId = value(recording, "id");
    String title = value(recording, "title");
    if (!validUuid(recordingId) || title == null) throw invalidData("recording_metadata");

    List<JsonNode> releases = new ArrayList<>();
    int offset = 0;
    while (true) {
      int pageOffset = offset;
      JsonNode page =
          requestUrl(
              baseUrl
                  + "/ws/2/release?recording="
                  + recordingId
                  + "&fmt=json&inc=artist-credits+release-groups&limit=100&offset="
                  + pageOffset,
              false,
              false,
              response -> validReleasePage(response, pageOffset, true));
      JsonNode entries = page.path("releases");
      int count = page.path("release-count").asInt(-1);
      if (!entries.isArray() || count < 0) throw invalidData("recording_release_page");
      int size = 0;
      for (JsonNode release : entries) {
        if (validUuid(value(release, "id"))
            && validUuid(value(release.path("release-group"), "id"))) releases.add(release);
        size++;
      }
      offset += size;
      if (offset >= count) break;
      if (size == 0) throw invalidData("recording_release_empty_page");
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
        selected == null ? null : selected.releaseGroupId(),
        selected == null ? null : selected.releaseDate());
  }

  public AlbumDetail getAlbumDetail(String id) {
    JsonNode group =
        requestUrl(
            baseUrl + "/ws/2/release-group/" + id + "?fmt=json&inc=artist-credits",
            true,
            response -> validUuid(value(response, "id")) && value(response, "title") != null);
    if (group == null) throw new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND);
    String groupId = value(group, "id");
    String title = value(group, "title");
    if (title == null || !validUuid(groupId)) throw invalidData("release_group_metadata");
    List<JsonNode> releases = new ArrayList<>();
    int offset = 0;
    while (true) {
      int pageOffset = offset;
      JsonNode page =
          requestUrl(
              baseUrl
                  + "/ws/2/release?release-group="
                  + groupId
                  + "&fmt=json&inc=artist-credits&limit=100&offset="
                  + pageOffset,
              false,
              false,
              response -> validReleasePage(response, pageOffset, false));
      JsonNode entries = page.path("releases");
      int count = page.path("release-count").asInt(-1);
      if (!entries.isArray() || count < 0) throw invalidData("album_release_page");
      int size = 0;
      for (JsonNode release : entries) {
        releases.add(release);
        size++;
      }
      offset += size;
      if (offset >= count) break;
      if (size == 0) throw invalidData("album_release_empty_page");
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
    if (!releases.isEmpty() && selected == null) throw invalidData("album_release_selection");
    List<AlbumTrack> tracks = new ArrayList<>();
    if (selected != null) {
      String releaseId = value(selected, "id");
      JsonNode release =
          requestUrl(
              baseUrl
                  + "/ws/2/release/"
                  + releaseId
                  + "?fmt=json&inc=recordings+artist-credits+media",
              true,
              this::validReleaseDetail);
      if (release == null || !release.path("media").isArray())
        throw invalidData(release == null ? "album_release_not_found" : "album_release_media");
      int discIndex = 0;
      for (JsonNode medium : release.path("media")) {
        int discNumber = medium.path("position").asInt(++discIndex);
        if (!medium.path("tracks").isArray()) throw invalidData("album_release_tracks");
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

  private BusinessException invalidData(String stage) {
    log.warn("MusicBrainz failure reason=invalid_data stage={}", stage);
    return new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
  }

  private JsonNode requestUrl(String url, boolean lookup) {
    return requestUrl(url, lookup, !lookup, JsonNode::isObject);
  }

  private JsonNode requestUrl(String url, boolean lookup, Predicate<JsonNode> validator) {
    return requestUrl(url, lookup, !lookup, validator);
  }

  private JsonNode requestUrl(String url, boolean lookup, boolean search) {
    return requestUrl(url, lookup, search, JsonNode::isObject);
  }

  private JsonNode requestUrl(
      String url, boolean lookup, boolean search, Predicate<JsonNode> validator) {

    CachedResponse cached = cache.get(url);

    if (isValid(cached)) {
      return cached.body();
    }

    String operation = lookup ? "lookup" : search ? "search" : "browse";
    long deadline = 0;
    int attempts = 0;
    int redirects = 0;
    int requests = 0;
    URI currentUri = URI.create(url);
    Set<URI> visited = new HashSet<>();
    visited.add(currentUri.normalize());
    while (true) {
      int attempt = attempts + 1;
      long waitStart = System.nanoTime();
      long waitBudget = requests == 0 ? TimeUnit.SECONDS.toNanos(4) : deadline - System.nanoTime();
      acquireRequestTurn(url, operation, waitBudget);
      long lockWaitMillis = elapsedMillis(waitStart);
      try {
        cached = cache.get(url);
        if (isValid(cached)) return cached.body();
        if (requests == 0) deadline = System.nanoTime() + REQUEST_BUDGET_NANOS;

        long remainingBudget = deadline - System.nanoTime();
        if (requests > 0 && remainingBudget <= 0) {
          log.warn(
              "MusicBrainz failure reason=retry_budget_exhausted operation={} attempt={} lock_wait_ms={}",
              operation,
              attempt,
              lockWaitMillis);
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
        }
        long timeoutNanos =
            requests == 0
                ? TimeUnit.SECONDS.toNanos(8)
                : Math.min(TimeUnit.SECONDS.toNanos(8), remainingBudget);
        HttpRequest request =
            HttpRequest.newBuilder(currentUri)
                .timeout(Duration.ofNanos(timeoutNanos))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        long requestStart = System.nanoTime();
        try {
          HttpResponse<String> response = sendWithTimeout(request, timeoutNanos);
          requests++;

          int status = response.statusCode();
          if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            if (++redirects > 3 || response.headers().allValues("Location").size() != 1) {
              throw invalidRedirect(operation, status);
            }
            URI target;
            try {
              target =
                  currentUri
                      .resolve(response.headers().firstValue("Location").orElseThrow())
                      .normalize();
            } catch (IllegalArgumentException exception) {
              throw invalidRedirect(operation, status);
            }
            if (!sameOrigin(URI.create(baseUrl), target)
                || target.getUserInfo() != null
                || target.getFragment() != null
                || target.getPath() == null
                || target.getRawPath().contains("%")
                || !target.getPath().startsWith("/ws/2/")
                || !sameResource(URI.create(url), target)
                || !visited.add(target)) {
              throw invalidRedirect(operation, status);
            }
            currentUri = target;
            continue;
          }

          if (lookup && response.statusCode() == 404) {
            return null;
          }

          if (search && response.statusCode() == 400) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
          }

          RetryAfter retryAfter = parseRetryAfter(response.headers().allValues("Retry-After"));
          if ((response.statusCode() == 503 || response.statusCode() == 429)
              && retryAfter.valid()
              && retryAfter.until().isAfter(serverCooldownUntil)) {
            serverCooldownUntil = retryAfter.until();
          }
          if (response.statusCode() == 503
              && attempt == 1
              && (!retryAfter.present() || retryAfter.valid())) {
            Duration delay =
                retryAfter.valid() ? remainingCooldown(retryAfter.until()) : Duration.ZERO;
            long slotWait = Math.max(0L, nextAllowedNanos - System.nanoTime());
            if (delay.compareTo(MAX_RETRY_AFTER) <= 0
                && deadline - System.nanoTime() > Math.max(delay.toNanos(), slotWait)) {
              log.warn(
                  "MusicBrainz failure reason=http_status operation={} http_status=503 attempt={} outcome=retry retry_after_ms={} lock_wait_ms={} request_elapsed_ms={}",
                  operation,
                  attempt,
                  delay.toMillis(),
                  lockWaitMillis,
                  elapsedMillis(requestStart));
              attempts++;
              continue;
            }
          }

          if (response.statusCode() != 200) {
            log.warn(
                "MusicBrainz failure reason=http_status operation={} http_status={} attempt={} outcome=final retry_after_ms={} lock_wait_ms={} request_elapsed_ms={}",
                operation,
                response.statusCode(),
                attempt,
                retryAfter.valid() ? safeMillis(remainingCooldown(retryAfter.until())) : -1,
                lockWaitMillis,
                elapsedMillis(requestStart));
            throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
          }

          JsonNode body;
          try {
            body = mapper.readTree(response.body());
          } catch (Exception exception) {
            log.warn(
                "MusicBrainz failure reason=response_parse operation={} http_status={} attempt={} outcome=final lock_wait_ms={} request_elapsed_ms={} exception_type={}",
                operation,
                response.statusCode(),
                attempt,
                lockWaitMillis,
                elapsedMillis(requestStart),
                exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
          }

          if (lookup) {
            String returnedId = value(body, "id");
            String path = response.uri().getPath();
            if (returnedId == null || !path.endsWith("/" + returnedId)) {
              log.warn(
                  "MusicBrainz failure reason=lookup_id_mismatch operation={} http_status={} attempt={} outcome=final lock_wait_ms={} request_elapsed_ms={}",
                  operation,
                  response.statusCode(),
                  attempt,
                  lockWaitMillis,
                  elapsedMillis(requestStart));
              throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
            }
          }

          if (!validator.test(body)) {
            throw invalidData(operation + "_response");
          }

          if (cache.size() >= 500) {
            cache.clear();
            browseCacheExpirations.clear();
          }

          cache.put(url, new CachedResponse(body, cacheExpiration(url, operation)));

          if (attempt > 1) {
            log.info(
                "MusicBrainz retry outcome=success operation={} http_status=200 attempt={} lock_wait_ms={} request_elapsed_ms={}",
                operation,
                attempt,
                lockWaitMillis,
                elapsedMillis(requestStart));
          }

          return body;

        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();

          log.warn(
              "MusicBrainz failure reason=request_interrupted operation={} attempt={} outcome=final lock_wait_ms={} request_elapsed_ms={} exception_type={}",
              operation,
              attempt,
              lockWaitMillis,
              elapsedMillis(requestStart),
              exception.getClass().getSimpleName());

          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);

        } catch (BusinessException exception) {
          throw exception;

        } catch (TimeoutException exception) {
          log.warn(
              "MusicBrainz failure reason=request_timeout operation={} attempt={} outcome=final lock_wait_ms={} request_elapsed_ms={} exception_type={}",
              operation,
              attempt,
              lockWaitMillis,
              elapsedMillis(requestStart),
              exception.getClass().getSimpleName());
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);

        } catch (Exception exception) {
          log.warn(
              "MusicBrainz failure reason=request_exception operation={} attempt={} outcome=final lock_wait_ms={} request_elapsed_ms={} exception_type={}",
              operation,
              attempt,
              lockWaitMillis,
              elapsedMillis(requestStart),
              exception.getClass().getSimpleName());
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
        }
      } finally {
        requestLock.unlock();
      }
    }
  }

  private HttpResponse<String> sendWithTimeout(HttpRequest request, long timeoutNanos)
      throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<HttpResponse<String>> response =
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    try {
      return response.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      response.cancel(true);
      throw exception;
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof java.net.http.HttpTimeoutException cause) {
        response.cancel(true);
        TimeoutException timeout = new TimeoutException("MusicBrainz response timed out");
        timeout.initCause(cause);
        throw timeout;
      }
      throw exception;
    }
  }

  private long cacheExpiration(String url, String operation) {
    long now = System.nanoTime();
    if (!"browse".equals(operation)) return now + CACHE_NANOS;
    String groupKey = browseCacheGroupKey(url);
    boolean firstPage = url.endsWith("offset=0");
    return browseCacheExpirations.compute(
        groupKey,
        (key, expiration) -> {
          if (firstPage || expiration == null || now - expiration >= 0) {
            cache.keySet().removeIf(cachedUrl -> browseCacheGroupKey(cachedUrl).equals(groupKey));
            return now + CACHE_NANOS;
          }
          return expiration;
        });
  }

  private String browseCacheGroupKey(String url) {
    return url.replaceAll("([?&])offset=[^&]*", "$1offset=*");
  }

  private boolean validReleasePage(JsonNode response, int offset, boolean requireReleaseGroup) {
    JsonNode count = response.path("release-count");
    JsonNode releases = response.path("releases");
    if (!releases.isArray()
        || !count.isIntegralNumber()
        || count.asLong(-1) < offset
        || (releases.isEmpty() && count.asLong() > offset)
        || count.asLong() < offset + releases.size()) return false;
    for (JsonNode release : releases) {
      if (!validUuid(value(release, "id"))) return false;
      if (requireReleaseGroup && !validUuid(value(release.path("release-group"), "id")))
        return false;
    }
    return true;
  }

  private boolean validReleaseDetail(JsonNode response) {
    if (!validUuid(value(response, "id")) || !response.path("media").isArray()) return false;
    for (JsonNode medium : response.path("media")) {
      if (!medium.path("tracks").isArray()) return false;
    }
    return true;
  }

  private BusinessException invalidRedirect(String operation, int status) {
    log.warn(
        "MusicBrainz failure reason=redirect_rejected operation={} http_status={} outcome=final",
        operation,
        status);
    return new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
  }

  private boolean sameOrigin(URI base, URI target) {
    if (base.getHost() == null
        || target.getHost() == null
        || base.getScheme() == null
        || target.getScheme() == null
        || !("http".equalsIgnoreCase(base.getScheme())
            || "https".equalsIgnoreCase(base.getScheme()))) return false;
    return base.getScheme().equalsIgnoreCase(target.getScheme())
        && base.getHost().equalsIgnoreCase(target.getHost())
        && effectivePort(base) == effectivePort(target);
  }

  private int effectivePort(URI uri) {
    if (uri.getPort() != -1) return uri.getPort();
    return "https".equalsIgnoreCase(uri.getScheme())
        ? 443
        : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : -1;
  }

  private boolean sameResource(URI original, URI target) {
    String prefix = "/ws/2/";
    if (!original.getPath().startsWith(prefix) || !target.getPath().startsWith(prefix))
      return false;
    String originalResource = original.getPath().substring(prefix.length()).split("/", 2)[0];
    String targetResource = target.getPath().substring(prefix.length()).split("/", 2)[0];
    return !originalResource.isEmpty() && originalResource.equals(targetResource);
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }

  private void acquireRequestTurn(String url, String operation, long waitBudget) {
    long start = System.nanoTime();
    while (true) {
      long remaining = waitBudget - (System.nanoTime() - start);
      if (remaining <= 0) {
        log.warn(
            "MusicBrainz failure reason=turn_timeout operation={} lock_wait_ms={}",
            operation,
            elapsedMillis(start));
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      }
      try {
        if (!requestLock.tryLock(
            Math.min(remaining, TimeUnit.SECONDS.toNanos(2)), TimeUnit.NANOSECONDS)) {
          log.warn(
              "MusicBrainz failure reason=lock_timeout operation={} lock_wait_ms={}",
              operation,
              elapsedMillis(start));
          throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        log.warn(
            "MusicBrainz failure reason=lock_interrupted operation={} lock_wait_ms={} exception_type={}",
            operation,
            elapsedMillis(start),
            exception.getClass().getSimpleName());
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
      }
      if (isValid(cache.get(url))) return;
      Duration cooldown = remainingCooldown(serverCooldownUntil);
      remaining = waitBudget - (System.nanoTime() - start);
      if (remaining <= 0 || cooldown.compareTo(Duration.ofNanos(remaining)) >= 0) {
        requestLock.unlock();
        log.warn(
            "MusicBrainz failure reason=server_cooldown operation={} retry_after_ms={} lock_wait_ms={}",
            operation,
            safeMillis(cooldown),
            elapsedMillis(start));
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      }
      long delay = Math.max(Math.max(0L, nextAllowedNanos - System.nanoTime()), cooldown.toNanos());
      if (delay == 0) {
        nextAllowedNanos = System.nanoTime() + REQUEST_INTERVAL_NANOS;
        return;
      }
      requestLock.unlock();
      if (delay >= remaining) {
        log.warn(
            "MusicBrainz failure reason=turn_timeout operation={} lock_wait_ms={}",
            operation,
            elapsedMillis(start));
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE);
      }
      try {
        TimeUnit.NANOSECONDS.sleep(delay);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        log.warn(
            "MusicBrainz failure reason=rate_wait_interrupted operation={} lock_wait_ms={} exception_type={}",
            operation,
            elapsedMillis(start),
            exception.getClass().getSimpleName());
        throw new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, exception);
      }
    }
  }

  private RetryAfter parseRetryAfter(List<String> values) {
    if (values.isEmpty()) return new RetryAfter(false, false, Instant.EPOCH);
    if (values.size() != 1) return new RetryAfter(true, false, Instant.EPOCH);
    String value = values.get(0).trim();
    if (value.isEmpty()) return new RetryAfter(true, false, Instant.EPOCH);
    if (value.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
      try {
        return new RetryAfter(true, true, Instant.now().plusSeconds(Long.parseLong(value)));
      } catch (NumberFormatException | DateTimeException | ArithmeticException exception) {
        return new RetryAfter(true, true, Instant.MAX);
      }
    }
    try {
      return new RetryAfter(
          true, true, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
    } catch (DateTimeParseException exception) {
      return new RetryAfter(true, false, Instant.EPOCH);
    }
  }

  private Duration remainingCooldown(Instant until) {
    Instant now = Instant.now();
    return until.isAfter(now) ? Duration.between(now, until) : Duration.ZERO;
  }

  private long safeMillis(Duration duration) {
    try {
      return duration.toMillis();
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private record RetryAfter(boolean present, boolean valid, Instant until) {}

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
