package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzResponseTimeoutTest {
  @Test
  void stalledResponseBodyTimesOutAndReleasesRequestLock() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ExecutorService serverExecutor = Executors.newCachedThreadPool();
    server.setExecutor(serverExecutor);
    server.createContext(
        "/ws/2/",
        exchange -> {
          int number = calls.incrementAndGet();
          if (number == 1) {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{\"count\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
              Thread.sleep(TimeUnit.SECONDS.toMillis(10));
              exchange
                  .getResponseBody()
                  .write("0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            } finally {
              exchange.close();
            }
            return;
          }
          byte[] body = "{\"count\":0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
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
      long started = System.nanoTime();
      BusinessException timeout =
          assertThrows(BusinessException.class, () -> provider.searchTracks("stalled", 10, 0));
      assertEquals(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, timeout.getErrorCode());
      assertTrue(
          System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10),
          "request timeout must include response body consumption");
      assertDoesNotThrow(() -> provider.searchTracks("healthy", 10, 0));
      assertEquals(2, calls.get(), "the next request should acquire requestLock after timeout");
    } finally {
      server.stop(0);
      serverExecutor.shutdownNow();
    }
  }
}
