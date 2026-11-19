package com.typeforsheet.backend.domain.search.provider;

import java.util.Set;
import tools.jackson.databind.JsonNode;

final class RepresentativeReleaseSelector {

  private static final Set<String> EXCLUDED_SECONDARY_TYPES =
      Set.of("compilation", "live", "remix", "dj-mix", "mixtape/street");

  private RepresentativeReleaseSelector() {}

  static SelectedRelease select(JsonNode recording) {

    String recordingArtistId = firstArtistId(recording.path("artist-credit"));

    SelectedRelease best = null;
    int bestScore = Integer.MIN_VALUE;
    String bestDate = null;

    for (JsonNode release : recording.path("releases")) {

      JsonNode group = release.path("release-group");
      String groupId = value(group, "id");

      if (groupId == null) {
        continue;
      }

      int currentScore = calculateScore(release, recordingArtistId);

      String currentDate = value(release, "date");

      boolean better =
          best == null
              || currentScore > bestScore
              || (currentScore == bestScore && isEarlier(currentDate, bestDate));

      if (better) {
        best = new SelectedRelease(value(release, "title"), groupId);

        bestScore = currentScore;
        bestDate = currentDate;
      }
    }

    return best;
  }

  private static int calculateScore(JsonNode release, String recordingArtistId) {

    int score = 0;

    if ("Official".equalsIgnoreCase(value(release, "status"))) {
      score += 400;
    }

    if (recordingArtistId != null && hasArtist(release, recordingArtistId)) {
      score += 200;
    }

    JsonNode group = release.path("release-group");

    if (!hasExcludedSecondaryType(group)) {
      score += 100;
    }

    String primaryType = value(group, "primary-type");

    if ("Album".equalsIgnoreCase(primaryType)) {
      score += 50;
    } else if ("EP".equalsIgnoreCase(primaryType)) {
      score += 20;
    } else if ("Single".equalsIgnoreCase(primaryType)) {
      score += 10;
    }

    return score;
  }

  private static boolean hasArtist(JsonNode release, String artistId) {

    for (JsonNode credit : release.path("artist-credit")) {
      String id = value(credit.path("artist"), "id");

      if (artistId.equals(id)) {
        return true;
      }
    }

    return false;
  }

  private static String firstArtistId(JsonNode credits) {

    for (JsonNode credit : credits) {
      String id = value(credit.path("artist"), "id");

      if (id != null) {
        return id;
      }
    }

    return null;
  }

  private static boolean hasExcludedSecondaryType(JsonNode group) {

    for (JsonNode type : group.path("secondary-types")) {
      if (EXCLUDED_SECONDARY_TYPES.contains(type.asText().toLowerCase(java.util.Locale.ROOT))) {
        return true;
      }
    }

    return false;
  }

  private static boolean isEarlier(String candidate, String current) {

    if (candidate == null) {
      return false;
    }

    if (current == null) {
      return true;
    }

    return candidate.compareTo(current) < 0;
  }

  private static String value(JsonNode node, String field) {

    JsonNode child = node.path(field);

    if (child.isMissingNode() || child.isNull()) {
      return null;
    }

    String text = child.asText();

    return text.isBlank() ? null : text;
  }

  record SelectedRelease(String albumTitle, String releaseGroupId) {}
}
