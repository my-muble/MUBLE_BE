package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzRetryAfterTest {
  @Test
  void retriesShortSecondsAndHttpDate() throws Exception {
    for (String header :
        List.of(
            "1",
            DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(2)))) {
      AtomicInteger calls = new AtomicInteger();
      List<Long> times = new CopyOnWriteArrayList<>();
      HttpServer server =
          server(
              exchange -> {
                times.add(System.nanoTime());
                int number = calls.incrementAndGet();
                if (number == 1) exchange.getResponseHeaders().add("Retry-After", header);
                byte[] body = "{\"count\":0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(number == 1 ? 503 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
              });
      try {
        assertTrue(provider(server).searchTracks("query", 10, 0).results().isEmpty());
        assertEquals(2, calls.get());
        assertTrue(times.get(1) - times.get(0) >= TimeUnit.MILLISECONDS.toNanos(900));
      } finally {
        server.stop(0);
      }
    }
  }

  @Test
  void doesNotRetryLongInvalidOrOverflowDelayAndHonors429Cooldown() throws Exception {
    for (String header : List.of("120", "not-a-date", "9999999999999999999999999999999999999")) {
      AtomicInteger calls = new AtomicInteger();
      HttpServer server =
          server(
              exchange -> {
                calls.incrementAndGet();
                exchange.getResponseHeaders().add("Retry-After", header);
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
              });
      try {
        assertEquals(
            ErrorCode.MUSIC_CATALOG_UNAVAILABLE,
            assertThrows(
                    BusinessException.class, () -> provider(server).searchTracks("query", 10, 0))
                .getErrorCode());
        assertEquals(1, calls.get());
      } finally {
        server.stop(0);
      }
    }
    AtomicInteger calls = new AtomicInteger();
    HttpServer server =
        server(
            exchange -> {
              calls.incrementAndGet();
              exchange.getResponseHeaders().add("Retry-After", "120");
              exchange.sendResponseHeaders(429, -1);
              exchange.close();
            });
    try {
      var provider = provider(server);
      assertThrows(BusinessException.class, () -> provider.searchTracks("first", 10, 0));
      assertThrows(BusinessException.class, () -> provider.searchTracks("second", 10, 0));
      assertEquals(1, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retryCooldownDoesNotHoldLockAndConcurrentRequestsRespectInterval() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<Long> times = new CopyOnWriteArrayList<>();
    CountDownLatch retryScheduled = new CountDownLatch(1);
    HttpServer server =
        server(
            exchange -> {
              times.add(System.nanoTime());
              int number = calls.incrementAndGet();
              if (number == 1) exchange.getResponseHeaders().add("Retry-After", "1");
              byte[] body = "{\"count\":0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(number == 1 ? 503 : 200, body.length);
              exchange.getResponseBody().write(body);
              exchange.close();
            });
    Logger logger = (Logger) LoggerFactory.getLogger(MusicBrainzCatalogProvider.class);
    AppenderBase<ILoggingEvent> appender =
        new AppenderBase<>() {
          @Override
          protected void append(ILoggingEvent event) {
            if (event.getFormattedMessage().contains("outcome=retry")) retryScheduled.countDown();
          }
        };
    appender.start();
    logger.addAppender(appender);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var provider = provider(server);
      Future<?> first = pool.submit(() -> provider.searchTracks("first", 10, 0));
      assertTrue(retryScheduled.await(5, TimeUnit.SECONDS));
      Future<?> second = pool.submit(() -> provider.searchTracks("second", 10, 0));
      first.get(8, TimeUnit.SECONDS);
      second.get(8, TimeUnit.SECONDS);
      assertEquals(3, calls.get());
      assertTrue(times.get(1) - times.get(0) >= TimeUnit.MILLISECONDS.toNanos(900));
      assertTrue(times.get(2) - times.get(1) >= TimeUnit.MILLISECONDS.toNanos(900));
    } finally {
      pool.shutdownNow();
      logger.detachAppender(appender);
      appender.stop();
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
