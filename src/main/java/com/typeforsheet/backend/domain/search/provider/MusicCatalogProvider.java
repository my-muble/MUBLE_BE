package com.typeforsheet.backend.domain.search.provider;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;

public interface MusicCatalogProvider {
  SearchPage<Track> searchTracks(String query, int limit, int offset);

  SearchPage<Album> searchAlbums(String query, int limit, int offset);

  SearchPage<Artist> searchArtists(String query, int limit, int offset);
}
