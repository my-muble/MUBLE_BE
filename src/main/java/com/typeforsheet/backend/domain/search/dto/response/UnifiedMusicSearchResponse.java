package com.typeforsheet.backend.domain.search.dto.response;

import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Album;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Artist;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.SearchPage;
import com.typeforsheet.backend.domain.search.dto.response.MusicSearchResponses.Track;

public record UnifiedMusicSearchResponse(
    SearchPage<Track> tracks, SearchPage<Album> albums, SearchPage<Artist> artists) {}
