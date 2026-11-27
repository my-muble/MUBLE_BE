package com.typeforsheet.backend.domain.music.controller;

final class MusicApiExamples {
  private MusicApiExamples() {}

  static final String AUTH =
      "{\"success\":false,\"code\":\"INVALID_ACCESS_TOKEN\",\"message\":\"유효하지 않거나 만료된 Access Token입니다.\",\"data\":null}";
  static final String INVALID =
      "{\"success\":false,\"code\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청입니다.\",\"data\":null}";
  static final String VALIDATION =
      "{\"success\":false,\"code\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청입니다.\",\"data\":{\"id\":\"공백일 수 없습니다\"}}";
  static final String INVALID_ID =
      "{\"success\":false,\"code\":\"INVALID_MUSIC_ID\",\"message\":\"유효하지 않은 음악 외부 ID입니다.\",\"data\":null}";
  static final String UNSUPPORTED =
      "{\"success\":false,\"code\":\"UNSUPPORTED_MUSIC_REFERENCE\",\"message\":\"지원하지 않는 음악 외부 ID 유형입니다.\",\"data\":null}";
  static final String EXTERNAL_NOT_FOUND =
      "{\"success\":false,\"code\":\"MUSIC_EXTERNAL_ID_NOT_FOUND\",\"message\":\"외부 음악을 찾을 수 없습니다.\",\"data\":null}";
  static final String ALBUM_NOT_FOUND =
      "{\"success\":false,\"code\":\"ALBUM_NOT_FOUND\",\"message\":\"앨범을 찾을 수 없습니다.\",\"data\":null}";
  static final String TRACK_NOT_FOUND =
      "{\"success\":false,\"code\":\"TRACK_NOT_FOUND\",\"message\":\"곡을 찾을 수 없습니다.\",\"data\":null}";
  static final String MAPPING_CONFLICT =
      "{\"success\":false,\"code\":\"MUSIC_MAPPING_CONFLICT\",\"message\":\"동일한 음악의 내부 ID 매핑이 충돌합니다.\",\"data\":null}";
  static final String MAPPING =
      "{\"success\":false,\"code\":\"MUSIC_MAPPING_MISSING\",\"message\":\"음악 외부 ID 매핑이 없습니다.\",\"data\":null}";
  static final String INTERNAL =
      "{\"success\":false,\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}";
  static final String UPSTREAM =
      "{\"success\":false,\"code\":\"MUSIC_CATALOG_UNAVAILABLE\",\"message\":\"외부 음악 검색 서비스를 사용할 수 없습니다.\",\"data\":null}";
  static final String RESOLVE_OK =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"음악 ID 조회에 성공했습니다.\",\"data\":{\"musicId\":123,\"type\":\"ALBUM\"}}";
  static final String ALBUM_OK =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"앨범 상세 조회에 성공했습니다.\",\"data\":{\"albumId\":123,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RELEASE_GROUP\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"},\"title\":\"Palette\",\"artistName\":\"IU\",\"artistId\":\"b1234567-1234-1234-1234-123456789abc\",\"releaseDate\":\"2017-04-21\",\"releaseType\":\"Album\",\"coverImageUrl\":null,\"tracks\":[{\"discNumber\":1,\"trackNumber\":\"1\",\"title\":\"Palette\",\"artistName\":\"IU\",\"durationMs\":217000,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"}],\"trackCount\":1,\"totalDurationMs\":217000}}";
  static final String TRACK_OK =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"곡 상세 조회에 성공했습니다.\",\"data\":{\"trackId\":456,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"c1234567-1234-1234-1234-123456789abc\"},\"title\":\"밤편지\",\"artistName\":\"IU\",\"artistId\":\"b1234567-1234-1234-1234-123456789abc\",\"durationMs\":253000,\"albumTitle\":\"Palette\",\"releaseGroupId\":\"a1234567-1234-1234-1234-123456789abc\",\"coverImageUrl\":null,\"releaseDate\":\"2017-04-21\"}}";
  static final String ALBUM_MULTI =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"albumId\":123,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RELEASE_GROUP\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"},\"title\":\"Palette\",\"artistName\":\"IU\",\"artistId\":null,\"releaseDate\":\"2017-04-21\",\"releaseType\":\"Album\",\"coverImageUrl\":null,\"tracks\":[{\"discNumber\":1,\"trackNumber\":\"1\",\"title\":\"Track\",\"artistName\":\"IU\",\"durationMs\":180000,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"},{\"discNumber\":2,\"trackNumber\":\"1\",\"title\":\"Track\",\"artistName\":\"IU\",\"durationMs\":240000,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"}],\"trackCount\":2,\"totalDurationMs\":420000}}";
  static final String ALBUM_MISSING_DURATION =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"albumId\":123,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RELEASE_GROUP\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"},\"title\":\"Palette\",\"artistName\":\"IU\",\"artistId\":null,\"releaseDate\":\"2017-04-21\",\"releaseType\":\"Album\",\"coverImageUrl\":null,\"tracks\":[{\"discNumber\":1,\"trackNumber\":\"1\",\"title\":\"Track\",\"artistName\":\"IU\",\"durationMs\":180000,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"},{\"discNumber\":1,\"trackNumber\":\"2\",\"title\":\"Track\",\"artistName\":\"IU\",\"durationMs\":null,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"}],\"trackCount\":2,\"totalDurationMs\":null}}";
  static final String ALBUM_EMPTY =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"albumId\":123,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RELEASE_GROUP\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"},\"title\":\"Palette\",\"artistName\":\"IU\",\"artistId\":null,\"releaseDate\":\"2017-04-21\",\"releaseType\":\"Album\",\"coverImageUrl\":null,\"tracks\":[],\"trackCount\":0,\"totalDurationMs\":null}}";
  static final String ALBUM_INCOMPLETE =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"albumId\":123,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RELEASE_GROUP\",\"id\":\"a1234567-1234-1234-1234-123456789abc\"},\"title\":\"Palette\",\"artistName\":\"IU\",\"artistId\":null,\"releaseDate\":\"2017-04-21\",\"releaseType\":\"Album\",\"coverImageUrl\":null,\"tracks\":[{\"discNumber\":1,\"trackNumber\":\"1\",\"title\":\"Track\",\"artistName\":\"IU\",\"durationMs\":180000,\"recordingId\":\"c1234567-1234-1234-1234-123456789abc\"}],\"trackCount\":null,\"totalDurationMs\":null}}";
  static final String TRACK_ALBUM_A =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"? ?? ??? ??????.\",\"data\":{\"trackId\":456,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"c1234567-1234-1234-1234-123456789abc\"},\"title\":\"???\",\"artistName\":\"IU\",\"artistId\":null,\"durationMs\":253000,\"albumTitle\":\"?? A\",\"releaseGroupId\":\"a1234567-1234-1234-1234-123456789abc\",\"coverImageUrl\":\"https://example.com/cover.jpg\",\"releaseDate\":\"2017-04-21\"}}";
  static final String TRACK_ALBUM_B =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"? ?? ??? ??????.\",\"data\":{\"trackId\":456,\"reference\":{\"provider\":\"MUSICBRAINZ\",\"idType\":\"RECORDING\",\"id\":\"c1234567-1234-1234-1234-123456789abc\"},\"title\":\"???\",\"artistName\":\"IU\",\"artistId\":null,\"durationMs\":253000,\"albumTitle\":\"?? B\",\"releaseGroupId\":\"b1234567-1234-1234-1234-123456789abc\",\"coverImageUrl\":null,\"releaseDate\":\"2018\"}}";
}
