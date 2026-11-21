package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzRetryTest {
  @Test
  void recoversWhenAlbumBrowseReturns503() throws Exception {
    AtomicInteger browseCalls = new AtomicInteger();
    String group = "a1234567-1234-1234-1234-123456789abc";
    HttpServer server =
        server(
            exchange -> {
              boolean lookup = exchange.getRequestURI().getPath().contains("release-group/");
              int status = lookup || browseCalls.incrementAndGet() > 1 ? 200 : 503;
              String json =
                  lookup
                      ? "{\"id\":\"" + group + "\",\"title\":\"Album\"}"
                      : "{\"release-count\":0,\"releases\":[]}";
              byte[] body = json.getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(status, body.length);
              exchange.getResponseBody().write(body);
              exchange.close();
            });
    try {
      assertEquals("Album", provider(server).getAlbumDetail(group).title());
      assertEquals(2, browseCalls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retries503OnceAndRespectsRequestInterval() throws Exception {
    List<Long> times = new CopyOnWriteArrayList<>();
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              times.add(System.nanoTime());
              int status = calls.incrementAndGet() == 1 ? 503 : 200;
              byte[] body = "{\"count\":0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(status, body.length);
              exchange.getResponseBody().write(body);
              exchange.close();
            });
    try {
      var result = provider(server).searchTracks("private-search", 10, 0);
      assertTrue(result.results().isEmpty());
      assertEquals(2, calls.get());
      assertTrue(
          times.get(1) - times.get(0) >= TimeUnit.MILLISECONDS.toNanos(900),
          "Server arrival times should be approximately one second apart");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retriesOnlyOnceAndKeepsExistingError() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              calls.incrementAndGet();
              exchange.sendResponseHeaders(503, -1);
              exchange.close();
            });
    try {
      var error =
          assertThrows(
              BusinessException.class, () -> provider(server).searchTracks("query", 10, 0));
      assertEquals(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, error.getErrorCode());
      assertEquals(2, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void doesNotRetry400404429OrRetryAfter() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              calls.incrementAndGet();
              String path = exchange.getRequestURI().getPath();
              int status =
                  path.contains("release-group/")
                      ? 429
                      : path.contains("artist/")
                          ? 503
                          : exchange.getRequestURI().getRawQuery().startsWith("query=") ? 400 : 404;
              if (status == 429 || status == 503)
                exchange.getResponseHeaders().add("Retry-After", "120");
              exchange.sendResponseHeaders(status, -1);
              exchange.close();
            });
    try {
      var provider = provider(server);
      assertEquals(
          ErrorCode.INVALID_REQUEST,
          assertThrows(BusinessException.class, () -> provider.searchTracks("query", 10, 0))
              .getErrorCode());
      assertEquals(1, calls.get());
      assertFalse(provider.exists("recording", "a1234567-1234-1234-1234-123456789abc"));
      assertEquals(2, calls.get());
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(
                  BusinessException.class,
                  () ->
                      provider(server)
                          .exists("release-group", "a1234567-1234-1234-1234-123456789abc"))
              .getErrorCode());
      assertEquals(3, calls.get());
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(
                  BusinessException.class, () -> provider(server).searchArtists("query", 10, 0))
              .getErrorCode());
      assertEquals(4, calls.get());
    } finally {
      server.stop(0);
    }
  }

  private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/ws/2/", handler);
    server.start();
    return server;
  }

  private MusicBrainzCatalogProvider provider(HttpServer server) {
    return new MusicBrainzCatalogProvider(
        JsonMapper.builder().build(), "http://127.0.0.1:" + server.getAddress().getPort(), "test");
  }
}
