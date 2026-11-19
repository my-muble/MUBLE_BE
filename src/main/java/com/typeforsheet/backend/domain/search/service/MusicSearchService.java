package com.typeforsheet.backend.domain.search.service;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import com.typeforsheet.backend.domain.search.provider.AlbumArtworkProvider;
import com.typeforsheet.backend.domain.search.provider.MusicCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class MusicSearchService {

  private final MusicCatalogProvider catalogProvider;
  private final AlbumArtworkProvider artworkProvider;

  private final ExecutorService artworkExecutor = Executors.newFixedThreadPool(6);

  public MusicSearchService(
      MusicCatalogProvider catalogProvider, AlbumArtworkProvider artworkProvider) {

    this.catalogProvider = catalogProvider;
    this.artworkProvider = artworkProvider;
  }

  public SearchPage<Track> searchTracks(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Track> page = catalogProvider.searchTracks(keyword, limit, offset);

    List<CompletableFuture<Track>> tasks =
        page.results().stream()
            .map(
                track ->
                    CompletableFuture.supplyAsync(
                        () -> track.withCover(findCoverSafely(track.releaseGroupId())),
                        artworkExecutor))
            .toList();

    List<Track> results = tasks.stream().map(CompletableFuture::join).toList();

    return new SearchPage<>(results, page.total(), page.limit(), page.offset());
  }

  public SearchPage<Album> searchAlbums(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    SearchPage<Album> page = catalogProvider.searchAlbums(keyword, limit, offset);

    List<CompletableFuture<Album>> tasks =
        page.results().stream()
            .map(
                album ->
                    CompletableFuture.supplyAsync(
                        () -> album.withCover(findCoverSafely(album.reference().id())),
                        artworkExecutor))
            .toList();

    List<Album> results = tasks.stream().map(CompletableFuture::join).toList();

    return new SearchPage<>(results, page.total(), page.limit(), page.offset());
  }

  public SearchPage<Artist> searchArtists(String query, int limit, int offset) {

    String keyword = validate(query, limit, offset);

    return catalogProvider.searchArtists(keyword, limit, offset);
  }

  private String findCoverSafely(String releaseGroupId) {
    try {
      return artworkProvider.findCoverUrl(releaseGroupId);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private String validate(String query, int limit, int offset) {

    if (query == null) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    String normalized = query.strip();

    if (normalized.isBlank()
        || normalized.codePointCount(0, normalized.length()) > 100
        || limit < 1
        || limit > 20
        || offset < 0
        || offset > 10000) {

      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return normalized;
  }

  @PreDestroy
  public void shutdown() {
    artworkExecutor.shutdown();
  }
}
