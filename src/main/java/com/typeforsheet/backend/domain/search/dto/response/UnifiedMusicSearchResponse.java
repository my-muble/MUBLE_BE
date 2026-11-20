package com.typeforsheet.backend.domain.search.dto.response;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;
import java.util.Map;

public record UnifiedMusicSearchResponse(
    SearchPage<Track> tracks,
    SearchPage<Album> albums,
    SearchPage<Artist> artists,
    Map<String, String> errors) {}
