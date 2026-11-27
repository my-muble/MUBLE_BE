package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzTrackDetailTest {
  private static final String REC = "a1234567-1234-1234-1234-123456789abc";
  private static final String NO_ALBUM = "b1234567-1234-1234-1234-123456789abc";
  private static final String RELEASE1 = "c1234567-1234-1234-1234-123456789abc";
  private static final String RELEASE2 = "d1234567-1234-1234-1234-123456789abc";
  private static final String GROUP1 = "e1234567-1234-1234-1234-123456789abc";
  private static final String GROUP2 = "f1234567-1234-1234-1234-123456789abc";
  private static final String ALIAS = "01234567-1234-1234-1234-123456789abc";
  private static final String MISSING = "11234567-1234-1234-1234-123456789abc";

  @Test
  void parsesRecordingSelectsAlbumAcrossPagesAndAllowsNoAlbum() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/ws/2/",
        exchange -> {
          requests.incrementAndGet();
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getRawQuery();
          String json;
          if (path.equals("/ws/2/release-group/" + ALIAS)) {
            exchange
                .getResponseHeaders()
                .add("Location", "/ws/2/release-group/" + GROUP1 + "?fmt=json&inc=artist-credits");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
            return;
          }
          if (path.equals("/ws/2/release-group/" + MISSING)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
          }
          if (path.equals("/ws/2/recording/" + REC) || path.equals("/ws/2/recording/" + NO_ALBUM)) {
            String id = path.substring(path.lastIndexOf('/') + 1);
            json =
                "{\"id\":\""
                    + id
                    + "\",\"title\":\"Track\",\"length\":1234,\"artist-credit\":[{\"name\":\"Artist\",\"artist\":{\"id\":\"artist-id\"}}]}";
          } else if (path.equals("/ws/2/release-group/" + GROUP1)
              || path.equals("/ws/2/release-group/" + GROUP2)) {
            String id = path.substring(path.lastIndexOf('/') + 1);
            json =
                "{\"id\":\""
                    + id
                    + "\",\"title\":\"Group "
                    + id
                    + "\",\"first-release-date\":\"1980\"}";
          } else if (query.contains(NO_ALBUM)) {
            json = "{\"release-count\":0,\"releases\":[]}";
          } else if (query.contains("offset=0")) {
            json =
                "{\"release-count\":2,\"releases\":[{\"id\":\""
                    + RELEASE1
                    + "\",\"title\":\"Compilation\",\"status\":\"Official\",\"date\":\"1990\",\"release-group\":{\"id\":\""
                    + GROUP1
                    + "\",\"primary-type\":\"Album\",\"secondary-types\":[\"Compilation\"]}}]}";
          } else {
            json =
                "{\"release-count\":2,\"releases\":[{\"id\":\""
                    + RELEASE2
                    + "\",\"title\":\"Original\",\"status\":\"Official\",\"date\":\"2000\",\"artist-credit\":[{\"artist\":{\"id\":\"artist-id\"}}],\"release-group\":{\"id\":\""
                    + GROUP2
                    + "\",\"primary-type\":\"Album\",\"secondary-types\":[]}}]}";
          }
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test");
      var track = provider.getTrackDetail(REC);
      assertEquals("Track", track.title());
      assertEquals("Artist", track.artistName());
      assertEquals("artist-id", track.artistId());
      assertEquals(1234L, track.durationMs());
      assertEquals("Original", track.albumTitle());
      assertEquals(GROUP2, track.releaseGroupId());
      assertEquals("2000", track.releaseDate());
      provider.getTrackDetail(REC);
      assertEquals(3, requests.get());
      var firstGroup = provider.getTrackDetail(REC, GROUP1);
      assertEquals(GROUP1, firstGroup.releaseGroupId());
      assertEquals("Group " + GROUP1, firstGroup.albumTitle());
      assertEquals("1980", firstGroup.releaseDate());
      var secondGroup = provider.getTrackDetail(REC, GROUP2);
      assertEquals(GROUP2, secondGroup.releaseGroupId());
      assertEquals("Group " + GROUP2, secondGroup.albumTitle());
      assertEquals("Original", provider.getTrackDetail(REC).albumTitle());
      assertEquals(5, requests.get());
      assertEquals(GROUP1, provider.getTrackDetail(REC, GROUP1).releaseGroupId());
      assertEquals(5, requests.get());
      assertEquals(GROUP1, provider.getTrackDetail(REC, ALIAS).releaseGroupId());
      assertEquals(
          ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND,
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(REC, MISSING))
              .getErrorCode());
      assertEquals(
          ErrorCode.INVALID_REQUEST,
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(NO_ALBUM, GROUP1))
              .getErrorCode());
      var noAlbum = provider.getTrackDetail(NO_ALBUM);
      assertNull(noAlbum.albumTitle());
      assertNull(noAlbum.releaseGroupId());
      assertNull(noAlbum.releaseDate());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void albumThenTrackReusesGroupAndDoesNotFetchAlbumTracksAgain() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger requests = new AtomicInteger();
    AtomicInteger releaseDetails = new AtomicInteger();
    server.createContext(
        "/ws/2/",
        exchange -> {
          requests.incrementAndGet();
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getRawQuery();
          String json;
          if (path.equals("/ws/2/release-group/" + GROUP1))
            json =
                "{\"id\":\""
                    + GROUP1
                    + "\",\"title\":\"Group title\",\"first-release-date\":\"1980\"}";
          else if (path.equals("/ws/2/recording/" + REC))
            json = "{\"id\":\"" + REC + "\",\"title\":\"Track\"}";
          else if (path.equals("/ws/2/release/" + RELEASE1)) {
            releaseDetails.incrementAndGet();
            json = "{\"id\":\"" + RELEASE1 + "\",\"media\":[{\"tracks\":[]}]}";
          } else if (path.equals("/ws/2/release") && query.contains("release-group="))
            json = "{\"release-count\":1,\"releases\":[{\"id\":\"" + RELEASE1 + "\"}]}";
          else
            json =
                "{\"release-count\":1,\"releases\":[{\"id\":\""
                    + RELEASE2
                    + "\",\"release-group\":{\"id\":\""
                    + GROUP1
                    + "\"}}]}";
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test");
      var album = provider.getAlbumDetail(GROUP1);
      assertEquals(3, requests.get());
      var track = provider.getTrackDetail(REC, GROUP1);
      assertEquals(5, requests.get());
      assertEquals(album.title(), track.albumTitle());
      assertEquals(album.releaseDate(), track.releaseDate());
      assertEquals(GROUP1, track.releaseGroupId());
      assertEquals(1, releaseDetails.get());
      provider.getTrackDetail(REC, GROUP1);
      assertEquals(5, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void incompleteReleaseBrowseIsAnUpstreamErrorNotNonmembership() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          String json =
              exchange.getRequestURI().getPath().contains("/recording/")
                  ? "{\"id\":\"" + REC + "\",\"title\":\"Track\"}"
                  : "{\"release-count\":1,\"releases\":[]}";
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test");
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(REC, GROUP1))
              .getErrorCode());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void mapsRecording404AndServiceFailure() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          exchange.sendResponseHeaders(
              exchange.getRequestURI().getPath().endsWith(REC) ? 404 : 503, -1);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test");
      assertEquals(
          ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND,
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(REC)).getErrorCode());
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(NO_ALBUM))
              .getErrorCode());
    } finally {
      server.stop(0);
    }
  }
}
