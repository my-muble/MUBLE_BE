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

class MusicBrainzRedirectTest {
  private static final String OLD = "a1234567-1234-1234-1234-123456789abc";
  private static final String CURRENT = "b1234567-1234-1234-1234-123456789abc";

  @Test
  void followsMergedIdWithRateLimitAndCachesOriginalRequest() throws Exception {
    List<Long> times = new CopyOnWriteArrayList<>();
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              times.add(System.nanoTime());
              calls.incrementAndGet();
              if (exchange.getRequestURI().getPath().endsWith(OLD)) {
                exchange
                    .getResponseHeaders()
                    .add("Location", "/ws/2/recording/" + CURRENT + "?fmt=json");
                exchange.sendResponseHeaders(301, -1);
              } else {
                byte[] body = ("{\"id\":\"" + CURRENT + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
              }
              exchange.close();
            });
    try {
      var provider = provider(server);
      assertTrue(provider.exists("recording", OLD));
      assertTrue(provider.exists("recording", OLD));
      assertEquals(2, calls.get());
      assertTrue(times.get(1) - times.get(0) >= TimeUnit.MILLISECONDS.toNanos(900));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsCrossOriginRedirectWithoutContactingTarget() throws Exception {
    AtomicInteger targetCalls = new AtomicInteger();
    HttpServer target =
        server(
            exchange -> {
              targetCalls.incrementAndGet();
              exchange.sendResponseHeaders(200, -1);
              exchange.close();
            });
    HttpServer source =
        server(
            exchange -> {
              exchange
                  .getResponseHeaders()
                  .add(
                      "Location",
                      "http://127.0.0.1:"
                          + target.getAddress().getPort()
                          + "/ws/2/recording/"
                          + CURRENT);
              exchange.sendResponseHeaders(302, -1);
              exchange.close();
            });
    try {
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider(source).exists("recording", OLD))
              .getErrorCode());
      assertEquals(0, targetCalls.get());
    } finally {
      source.stop(0);
      target.stop(0);
    }
  }

  @Test
  void rejectsRedirectToDifferentEntityType() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              calls.incrementAndGet();
              exchange
                  .getResponseHeaders()
                  .add("Location", "/ws/2/artist/" + CURRENT + "?fmt=json");
              exchange.sendResponseHeaders(301, -1);
              exchange.close();
            });
    try {
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider(server).exists("recording", OLD))
              .getErrorCode());
      assertEquals(1, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsRedirectLoopAndMissingLocation() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              calls.incrementAndGet();
              if (exchange.getRequestURI().getPath().endsWith(OLD)) {
                exchange
                    .getResponseHeaders()
                    .add("Location", "/ws/2/recording/" + OLD + "?fmt=json");
              }
              exchange.sendResponseHeaders(301, -1);
              exchange.close();
            });
    try {
      var provider = provider(server);
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider.exists("recording", OLD))
              .getErrorCode());
      assertEquals(1, calls.get());
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> provider.exists("recording", CURRENT))
              .getErrorCode());
      assertEquals(2, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void followsRedirectThenRetries503WithoutBypassingLimiter() throws Exception {
    List<Long> times = new CopyOnWriteArrayList<>();
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              times.add(System.nanoTime());
              int n = calls.incrementAndGet();
              if (n == 1) {
                exchange
                    .getResponseHeaders()
                    .add("Location", "/ws/2/recording/" + CURRENT + "?fmt=json");
                exchange.sendResponseHeaders(301, -1);
              } else if (n == 2) {
                exchange.sendResponseHeaders(503, -1);
              } else {
                byte[] body = ("{\"id\":\"" + CURRENT + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
              }
              exchange.close();
            });
    try {
      assertTrue(provider(server).exists("recording", OLD));
      assertEquals(3, calls.get());
      assertTrue(times.get(1) - times.get(0) >= TimeUnit.MILLISECONDS.toNanos(900));
      assertTrue(times.get(2) - times.get(1) >= TimeUnit.MILLISECONDS.toNanos(900));
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
