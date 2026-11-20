package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzResponseValidationTest {
  private static final String RECORDING = "a1234567-1234-1234-1234-123456789abc";
  private static final String GROUP = "b1234567-1234-1234-1234-123456789abc";
  private static final String RELEASE = "c1234567-1234-1234-1234-123456789abc";

  @Test
  void malformedDetailResponseIsNotCached() throws Exception {
    AtomicInteger recordingRequests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String json;
          if (path.equals("/ws/2/recording/" + RECORDING)) {
            json =
                recordingRequests.incrementAndGet() == 1
                    ? "{\"id\":\"" + RECORDING + "\"}"
                    : "{\"id\":\"" + RECORDING + "\",\"title\":\"Song\"}";
          } else {
            json = "{\"release-count\":0,\"releases\":[]}";
          }
          byte[] body = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var provider = provider(server);
      BusinessException invalid =
          assertThrows(BusinessException.class, () -> provider.getTrackDetail(RECORDING));
      assertEquals(ErrorCode.MUSIC_CATALOG_UNAVAILABLE, invalid.getErrorCode());
      assertEquals("Song", provider.getTrackDetail(RECORDING).title());
      assertEquals(2, recordingRequests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void browsePagesUseOneSharedCacheExpiry() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ws/2/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getRawQuery();
          String json;
          if (path.equals("/ws/2/release-group/" + GROUP)) {
            json = "{\"id\":\"" + GROUP + "\",\"title\":\"Album\"}";
          } else if (query != null && query.contains("release-group=")) {
            json =
                query.contains("offset=0")
                    ? "{\"release-count\":2,\"releases\":[{\"id\":\"" + RELEASE + "\"}]}"
                    : "{\"release-count\":2,\"releases\":[{\"id\":\"d1234567-1234-1234-1234-123456789abc\"}]}";
          } else {
            json = "{\"id\":\"" + RELEASE + "\",\"media\":[]}";
          }
          byte[] body = json.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var provider = provider(server);
      provider.getAlbumDetail(GROUP);

      Field cacheField = MusicBrainzCatalogProvider.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      Map<String, ?> cache = (Map<String, ?>) cacheField.get(provider);
      List<?> browsePages =
          cache.entrySet().stream()
              .filter(entry -> entry.getKey().contains("release-group="))
              .map(Map.Entry::getValue)
              .toList();
      assertEquals(2, browsePages.size());
      Method expiryAccessor = browsePages.get(0).getClass().getDeclaredMethod("expiresAtNanos");
      expiryAccessor.setAccessible(true);
      Object expectedExpiry = expiryAccessor.invoke(browsePages.get(0));
      assertEquals(expectedExpiry, expiryAccessor.invoke(browsePages.get(1)));
    } finally {
      server.stop(0);
    }
  }

  private MusicBrainzCatalogProvider provider(HttpServer server) {
    return new MusicBrainzCatalogProvider(
        JsonMapper.builder().build(), "http://127.0.0.1:" + server.getAddress().getPort(), "test");
  }
}
