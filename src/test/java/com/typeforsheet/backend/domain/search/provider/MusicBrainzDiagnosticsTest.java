package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzDiagnosticsTest {
  @Test
  void lockTimeoutIsLoggedWithoutSendingASecondRequest() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger count = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/recording/",
        exchange -> {
          count.incrementAndGet();
          received.countDown();
          try {
            if (!release.await(7, TimeUnit.SECONDS))
              throw new IllegalStateException("test timeout");
            byte[] bytes = "{\"count\":0,\"recordings\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    Logger logger = (Logger) LoggerFactory.getLogger(MusicBrainzCatalogProvider.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    server.start();
    try {
      var provider = provider(server);
      Future<?> first =
          executor.submit(() -> provider.searchTracks("sensitive-first-query", 10, 0));
      assertTrue(received.await(5, TimeUnit.SECONDS));
      var failure =
          assertThrows(
              BusinessException.class,
              () -> provider.searchTracks("sensitive-second-query", 10, 0));
      assertEquals(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, failure.getErrorCode());
      assertEquals(1, count.get());
      assertTrue(
          appender.list.stream()
              .anyMatch(
                  e ->
                      e.getFormattedMessage().contains("reason=lock_timeout")
                          && e.getFormattedMessage().contains("lock_wait_ms=")));
      assertFalse(
          appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("sensitive-")));
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
      server.stop(0);
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void logsHttpStatusParsingAndValidationFailuresWithoutPayload() throws Exception {
    AtomicInteger count = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          int call = count.incrementAndGet();
          int status = call == 1 ? 429 : call == 2 || call == 3 ? 503 : 200;
          String body =
              call == 4
                  ? "{sensitive-response"
                  : "{\"id\":\"a1234567-1234-1234-1234-123456789abc\"}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    Logger logger = (Logger) LoggerFactory.getLogger(MusicBrainzCatalogProvider.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    server.start();
    try {
      var provider = provider(server);
      assertThrows(
          BusinessException.class, () -> provider.searchTracks("private-query-one", 10, 0));
      assertThrows(
          BusinessException.class, () -> provider.searchTracks("private-query-two", 10, 0));
      assertThrows(
          BusinessException.class, () -> provider.searchTracks("private-query-three", 10, 0));
      assertThrows(
          BusinessException.class,
          () -> provider.getAlbumDetail("a1234567-1234-1234-1234-123456789abc"));
      List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertTrue(
          logs.stream()
              .anyMatch(
                  s ->
                      s.contains("reason=http_status")
                          && s.contains("http_status=429")
                          && s.contains("request_elapsed_ms=")));
      assertTrue(
          logs.stream()
              .anyMatch(s -> s.contains("reason=http_status") && s.contains("http_status=503")));
      assertTrue(
          logs.stream()
              .anyMatch(s -> s.contains("reason=response_parse") && s.contains("exception_type=")));
      assertTrue(
          logs.stream()
              .anyMatch(
                  s ->
                      s.contains("reason=invalid_data")
                          && s.contains("stage=release_group_metadata")));
      assertTrue(
          logs.stream()
              .noneMatch(
                  s ->
                      s.contains("private-query")
                          || s.contains("sensitive-response")
                          || s.contains("a1234567")));
    } finally {
      server.stop(0);
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private MusicBrainzCatalogProvider provider(HttpServer server) {
    return new MusicBrainzCatalogProvider(
        JsonMapper.builder().build(), "http://127.0.0.1:" + server.getAddress().getPort(), "test");
  }
}
