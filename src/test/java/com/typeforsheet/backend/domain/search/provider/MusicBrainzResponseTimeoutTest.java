package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void interruptedResponseWaitCancelsRequestRestoresInterruptAndReleasesLock() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch responseStarted = new CountDownLatch(1);
    CountDownLatch clientCancellationObserved = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ExecutorService serverExecutor = Executors.newCachedThreadPool();
    server.setExecutor(serverExecutor);
    server.createContext(
        "/ws/2/",
        exchange -> {
          if (calls.incrementAndGet() == 1) {
            exchange.sendResponseHeaders(200, 0);
            try {
              exchange.getResponseBody().write("{\"count\":".getBytes(StandardCharsets.UTF_8));
              exchange.getResponseBody().flush();
              responseStarted.countDown();
              long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
              while (System.nanoTime() < deadline) {
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
                Thread.sleep(25);
              }
            } catch (IOException exception) {
              clientCancellationObserved.countDown();
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
      AtomicBoolean interruptRestored = new AtomicBoolean();
      AtomicReference<ErrorCode> errorCode = new AtomicReference<>();
      Thread requestThread =
          new Thread(
              () -> {
                try {
                  provider.searchTracks("interrupted", 10, 0);
                } catch (BusinessException exception) {
                  errorCode.set(exception.getErrorCode());
                  interruptRestored.set(Thread.currentThread().isInterrupted());
                }
              });
      requestThread.start();
      assertTrue(responseStarted.await(5, TimeUnit.SECONDS));
      requestThread.interrupt();
      requestThread.join(TimeUnit.SECONDS.toMillis(5));

      assertFalse(requestThread.isAlive(), "interrupted request should stop waiting");
      assertEquals(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, errorCode.get());
      assertTrue(interruptRestored.get(), "the provider should restore the interrupt flag");
      assertTrue(
          clientCancellationObserved.await(2, TimeUnit.SECONDS),
          "the HTTP client should cancel the in-flight response body");
      assertDoesNotThrow(() -> provider.searchTracks("after-interrupt", 10, 0));
      assertEquals(2, calls.get(), "the next request should acquire requestLock");
    } finally {
      server.stop(0);
      serverExecutor.shutdownNow();
    }
  }
}
