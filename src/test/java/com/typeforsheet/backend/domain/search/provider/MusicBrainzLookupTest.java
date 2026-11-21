package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzLookupTest {
  @Test
  void handlesLookupAndCachesSuccessfulResponses() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger calls = new AtomicInteger();
    server.createContext(
        "/ws/2/recording",
        exchange -> {
          calls.incrementAndGet();
          String path = exchange.getRequestURI().getPath();
          int status = path.contains("a123") ? 200 : path.contains("b123") ? 404 : 503;
          byte[] body =
              (status == 200 ? "{\"id\":\"a1234567-1234-1234-1234-123456789abc\"}" : "{}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new MusicBrainzCatalogProvider(
              JsonMapper.builder().build(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test");
      assertTrue(provider.exists("recording", "a1234567-1234-1234-1234-123456789abc"));
      assertTrue(provider.exists("recording", "a1234567-1234-1234-1234-123456789abc"));
      assertEquals(1, calls.get());
      assertFalse(provider.exists("recording", "b1234567-1234-1234-1234-123456789abc"));
      assertEquals(
          ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
          assertThrows(
                  BusinessException.class,
                  () -> provider.exists("recording", "c1234567-1234-1234-1234-123456789abc"))
              .getErrorCode());
    } finally {
      server.stop(0);
    }
  }
}
