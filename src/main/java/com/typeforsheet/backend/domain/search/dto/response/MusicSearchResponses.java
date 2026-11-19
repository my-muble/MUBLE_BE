package com.typeforsheet.backend.domain.search.dto.response;

import java.util.List;

public final class MusicSearchResponses {

  private MusicSearchResponses() {}

  public record ExternalReference(String provider, String idType, String id) {}

  public record SearchPage<T>(List<T> results, int total, int limit, int offset) {

    public SearchPage {
      results = List.copyOf(results);
    }
  }

  public record Track(
      ExternalReference reference,
      String title,
      String artistName,
      String artistId,
      String albumTitle,
      String releaseGroupId,
      String coverImageUrl,
      int score) {

    public Track withCover(String url) {
      return new Track(
          reference, title, artistName, artistId, albumTitle, releaseGroupId, url, score);
    }
  }

  public record Album(
      ExternalReference reference,
      String title,
      String artistName,
      String artistId,
      String releaseDate,
      String releaseType,
      String coverImageUrl,
      int score) {

    public Album withCover(String url) {
      return new Album(
          reference, title, artistName, artistId, releaseDate, releaseType, url, score);
    }
  }

  public record Artist(
      ExternalReference reference,
      String name,
      String artistType,
      String disambiguation,
      String imageUrl,
      int score) {}
}
