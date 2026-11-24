package com.typeforsheet.backend.domain.search.dto.response;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import java.util.List;
import java.util.Map;

public record MusicSearchPreviewResponse(
    SearchPage<Track> tracks,
    SearchPage<Album> albums,
    SearchPage<Artist> artists,
    List<String> displayOrder,
    Map<String, String> errors) {

  public MusicSearchPreviewResponse {
    displayOrder = List.copyOf(displayOrder);
    errors = Map.copyOf(errors);
  }
}
