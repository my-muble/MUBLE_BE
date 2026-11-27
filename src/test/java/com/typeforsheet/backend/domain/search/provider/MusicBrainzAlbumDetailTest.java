package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzAlbumDetailTest {
  private static final String GROUP = "a1234567-1234-1234-1234-123456789abc";
  private static final String R1 = "b1234567-1234-1234-1234-123456789abc";
  private static final String R2 = "c1234567-1234-1234-1234-123456789abc";
  private static final String R3 = "d1234567-1234-1234-1234-123456789abc";
  private static final String REC = "e1234567-1234-1234-1234-123456789abc";

  @Test
  void pagesReleasesSelectsOfficialEarliestAndParsesTracks() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger count = new AtomicInteger();
    server.createContext(
        "/ws/2/",
        exchange -> {
          count.incrementAndGet();
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getRawQuery();
          String json;
          if (path.equals("/ws/2/release-group/" + GROUP)) {
            json =
                "{\"id\":\""
                    + GROUP
                    + "\",\"title\":\"Album\",\"first-release-date\":\"1999\",\"primary-type\":\"Album\",\"artist-credit\":[{\"name\":\"Artist\",\"artist\":{\"id\":\"artist-id\"}}]}";
          } else if (path.equals("/ws/2/release") && query.contains("offset=0")) {
            json =
                "{\"release-count\":3,\"releases\":[{\"id\":\""
                    + R1
                    + "\",\"status\":\"Bootleg\",\"date\":\"1990\"},{\"id\":\""
                    + R2
                    + "\",\"status\":\"Official\",\"date\":\"2001\"}]}";
          } else if (path.equals("/ws/2/release")) {
            json =
                "{\"release-count\":3,\"releases\":[{\"id\":\""
                    + R3
                    + "\",\"status\":\"Official\",\"date\":\"2000\"}]}";
          } else if (path.equals("/ws/2/release/" + R3)) {
            json =
                "{\"id\":\""
                    + R3
                    + "\",\"media\":[{\"position\":1,\"tracks\":[{\"number\":\"A1\",\"title\":\"Track title\",\"length\":1234,\"artist-credit\":[{\"name\":\"Guest\"}],\"recording\":{\"id\":\""
                    + REC
                    + "\",\"title\":\"Recording title\",\"length\":9999}},{\"number\":\"2\",\"recording\":{\"id\":\""
                    + REC
                    + "\",\"title\":\"Fallback\",\"length\":5000}}]}]}";
          } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
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
      var album = provider.getAlbumDetail(GROUP);
      assertTrue(album.tracksComplete());
      assertEquals("Artist", album.artistName());
      assertEquals("artist-id", album.artistId());
      assertEquals(2, album.tracks().size());
      var first = album.tracks().get(0);
      assertEquals("A1", first.trackNumber());
      assertEquals("Track title", first.title());
      assertEquals("Guest", first.artistName());
      assertEquals(1234L, first.durationMs());
      assertEquals(REC, first.recordingId());
      assertEquals("Fallback", album.tracks().get(1).title());
      assertEquals("Artist", album.tracks().get(1).artistName());
      assertEquals(5000L, album.tracks().get(1).durationMs());
      assertEquals(4, count.get());
      provider.getAlbumDetail(GROUP);
      assertEquals(4, count.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void distinguishesAbsentReleaseAndDeclaredIncompleteLists() throws Exception {
    assertFalse(readAlbum(null).tracksComplete());
    assertTrue(readAlbum("{\"media\":[]}").tracksComplete());
    assertTrue(readAlbum("{\"media\":[{\"tracks\":[]}]}").tracksComplete());
    assertFalse(readAlbum("{\"medium-count\":2,\"media\":[{\"tracks\":[]}]}").tracksComplete());
    assertFalse(readAlbum("{\"media\":[{\"track-count\":2,\"tracks\":[{}]}]}").tracksComplete());
    assertFalse(readAlbum("{\"media\":[{\"track-count\":0,\"tracks\":[{}]}]}").tracksComplete());
    assertTrue(
        readAlbum(
                "{\"medium-count\":2,\"media\":[{\"track-count\":1,\"tracks\":[{}]},{\"track-count\":1,\"tracks\":[{}]}]}")
            .tracksComplete());
    var partial = readAlbum("{\"media\":[{\"track-count\":2,\"tracks\":[{\"number\":\"A1\"}]}]}");
    assertFalse(partial.tracksComplete());
    assertEquals("A1", partial.tracks().get(0).trackNumber());
  }

  @Test
  void malformedMediaAndTracksKeepExistingErrorPolicy() throws Exception {
    assertEquals(
        ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
        assertThrows(BusinessException.class, () -> readAlbum("{}")).getErrorCode());
    assertEquals(
        ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
        assertThrows(BusinessException.class, () -> readAlbum("{\"media\":[{}]}")).getErrorCode());
  }

  private MusicBrainzCatalogProvider.AlbumDetail readAlbum(String detail) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String json;
          if (path.contains("release-group/"))
            json = "{\"id\":\"" + GROUP + "\",\"title\":\"Album\"}";
          else if (path.endsWith("/release"))
            json =
                detail == null
                    ? "{\"release-count\":0,\"releases\":[]}"
                    : "{\"release-count\":1,\"releases\":[{\"id\":\"" + R1 + "\"}]}";
          else
            json =
                "{\"id\":\"" + R1 + "\"" + (detail.equals("{}") ? "}" : "," + detail.substring(1));
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      return new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test")
          .getAlbumDetail(GROUP);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void handlesNotFoundAndUpstreamFailure() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          exchange.sendResponseHeaders(
              exchange.getRequestURI().getPath().endsWith(GROUP) ? 404 : 503, -1);
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
          assertThrows(BusinessException.class, () -> provider.getAlbumDetail(GROUP))
              .getErrorCode());
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider.getAlbumDetail(R1)).getErrorCode());
    } finally {
      server.stop(0);
    }
  }
}
