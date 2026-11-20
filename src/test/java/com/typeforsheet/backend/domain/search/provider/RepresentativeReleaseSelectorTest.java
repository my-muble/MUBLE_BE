package com.typeforsheet.backend.domain.search.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RepresentativeReleaseSelectorTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void prefersOriginalAlbumOverCompilation() {

    JsonNode recording =
        mapper.readTree(
            """
                {
                  "artist-credit": [
                    {
                      "artist": {
                        "id": "iu-artist-id"
                      }
                    }
                  ],
                  "releases": [
                    {
                      "title": "Smash Hits 2",
                      "status": "Official",
                      "date": "2020-01-01",
                      "artist-credit": [
                        {
                          "artist": {
                            "id": "various-artists"
                          }
                        }
                      ],
                      "release-group": {
                        "id": "compilation-id",
                        "primary-type": "Album",
                        "secondary-types": ["Compilation"]
                      }
                    },
                    {
                      "title": "Palette",
                      "status": "Official",
                      "date": "2017-04-21",
                      "artist-credit": [
                        {
                          "artist": {
                            "id": "iu-artist-id"
                          }
                        }
                      ],
                      "release-group": {
                        "id": "palette-id",
                        "primary-type": "Album"
                      }
                    }
                  ]
                }
                """);

    RepresentativeReleaseSelector.SelectedRelease result =
        RepresentativeReleaseSelector.select(recording);

    assertEquals("Palette", result.albumTitle());
    assertEquals("palette-id", result.releaseGroupId());
    assertEquals("2017-04-21", result.releaseDate());
  }

  @Test
  void returnsNullDateWhenSelectedReleaseHasNoDate() {
    JsonNode recording =
        mapper.readTree(
            """
        {"releases":[
          {"title":"Official Album","status":"Official","release-group":{"id":"group-1","primary-type":"Album"}},
          {"title":"Bootleg","status":"Bootleg","date":"1990","release-group":{"id":"group-2","primary-type":"Album"}}
        ]}
        """);
    var selected = RepresentativeReleaseSelector.select(recording);
    assertEquals("Official Album", selected.albumTitle());
    assertNull(selected.releaseDate());
  }

  @Test
  void prefersOfficialReleaseOverBootleg() {

    JsonNode recording =
        mapper.readTree(
            """
                {
                  "releases": [
                    {
                      "title": "Bootleg Album",
                      "status": "Bootleg",
                      "date": "2010-01-01",
                      "release-group": {
                        "id": "bootleg-id",
                        "primary-type": "Album"
                      }
                    },
                    {
                      "title": "Official Album",
                      "status": "Official",
                      "date": "2015-01-01",
                      "release-group": {
                        "id": "official-id",
                        "primary-type": "Album"
                      }
                    }
                  ]
                }
                """);

    RepresentativeReleaseSelector.SelectedRelease result =
        RepresentativeReleaseSelector.select(recording);

    assertEquals("official-id", result.releaseGroupId());
  }

  @Test
  void prefersEarlierReleaseWhenScoresMatch() {

    JsonNode recording =
        mapper.readTree(
            """
                {
                  "releases": [
                    {
                      "title": "Later Album",
                      "status": "Official",
                      "date": "2024-01-01",
                      "release-group": {
                        "id": "later-id",
                        "primary-type": "Album"
                      }
                    },
                    {
                      "title": "Earlier Album",
                      "status": "Official",
                      "date": "2017-04-21",
                      "release-group": {
                        "id": "earlier-id",
                        "primary-type": "Album"
                      }
                    }
                  ]
                }
                """);

    RepresentativeReleaseSelector.SelectedRelease result =
        RepresentativeReleaseSelector.select(recording);

    assertEquals("earlier-id", result.releaseGroupId());
  }

  @Test
  void returnsNullWhenNoReleaseGroupExists() {

    JsonNode recording =
        mapper.readTree(
            """
                {
                  "releases": [
                    {
                      "title": "Unknown Release"
                    }
                  ]
                }
                """);

    assertNull(RepresentativeReleaseSelector.select(recording));
  }

  @Test
  void prefersRegularAlbumWhenArtistCreditIsMissing() {

    JsonNode recording =
        mapper.readTree(
            """
                {
                  "artist-credit": [
                    {
                      "artist": {
                        "id": "iu-artist-id"
                      }
                    }
                  ],
                  "releases": [
                    {
                      "title": "Compilation",
                      "status": "Official",
                      "artist-credit": [
                        {
                          "artist": {
                            "id": "iu-artist-id"
                          }
                        }
                      ],
                      "release-group": {
                        "id": "compilation-id",
                        "primary-type": "Album",
                        "secondary-types": ["Compilation"]
                      }
                    },
                    {
                      "title": "Palette",
                      "status": "Official",
                      "release-group": {
                        "id": "palette-id",
                        "primary-type": "Album"
                      }
                    }
                  ]
                }
                """);

    RepresentativeReleaseSelector.SelectedRelease result =
        RepresentativeReleaseSelector.select(recording);

    assertEquals("palette-id", result.releaseGroupId());
  }
}
