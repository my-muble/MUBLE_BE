package com.typeforsheet.backend.domain.record.controller;

final class RecordApiExamples {
  private RecordApiExamples() {}

  static final String INVALID =
      "{\"success\":false,\"code\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청입니다.\",\"data\":null}";
  static final String AUTH =
      "{\"success\":false,\"code\":\"INVALID_ACCESS_TOKEN\",\"message\":\"유효하지 않거나 만료된 Access Token입니다.\",\"data\":null}";
  static final String DENIED =
      "{\"success\":false,\"code\":\"ACCESS_DENIED\",\"message\":\"접근 권한이 없습니다.\",\"data\":null}";
  static final String USER =
      "{\"success\":false,\"code\":\"USER_NOT_FOUND\",\"message\":\"존재하지 않는 회원입니다.\",\"data\":null}";
  static final String MUSIC =
      "{\"success\":false,\"code\":\"MUSIC_ITEM_NOT_FOUND\",\"message\":\"음악을 찾을 수 없습니다.\",\"data\":null}";
  static final String RECORD =
      "{\"success\":false,\"code\":\"RECORD_NOT_FOUND\",\"message\":\"기록을 찾을 수 없습니다.\",\"data\":null}";
  static final String CONFLICT =
      "{\"success\":false,\"code\":\"IDEMPOTENCY_KEY_CONFLICT\",\"message\":\"이미 다른 요청에 사용된 멱등성 키입니다.\",\"data\":null}";
  static final String DELETED =
      "{\"success\":false,\"code\":\"RECORD_ALREADY_DELETED\",\"message\":\"이미 삭제된 기록입니다.\",\"data\":null}";
  static final String RETRY =
      "{\"success\":false,\"code\":\"RECORD_REQUEST_RETRY\",\"message\":\"요청을 다시 시도해 주세요.\",\"data\":null}";
  static final String INTERNAL =
      "{\"success\":false,\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}";
  static final String MAPPING =
      "{\"success\":false,\"code\":\"MUSIC_MAPPING_MISSING\",\"message\":\"음악 외부 ID 매핑이 없습니다.\",\"data\":null}";
  static final String REQUEST_CREATE =
      "{\"musicId\":123,\"rating\":4.5,\"content\":\"다시 듣고 싶은 앨범\",\"isPrivate\":false}";
  static final String REQUEST_PATCH = "{\"rating\":null,\"content\":\"새 감상\",\"isPrivate\":true}";
  static final String CREATED =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"음악 기록 작성에 성공했습니다.\",\"data\":{\"recordId\":1,\"musicId\":123,\"musicType\":\"ALBUM\",\"rating\":4.5,\"content\":\"다시 듣고 싶은 앨범\",\"isPrivate\":false,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\"}}";
  static final String UPDATED =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"음악 기록 수정에 성공했습니다.\",\"data\":{\"recordId\":1,\"musicId\":123,\"musicType\":\"ALBUM\",\"rating\":null,\"content\":\"새 감상\",\"isPrivate\":true,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:05:00\"}}";
  static final String DETAIL =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"음악 기록 조회에 성공했습니다.\",\"data\":{\"recordId\":1,\"authorId\":10,\"authorNickname\":\"뮤블사용자\",\"authorProfileImageUrl\":null,\"music\":{\"musicId\":123,\"type\":\"ALBUM\",\"title\":null,\"artistName\":null,\"coverImageUrl\":null},\"rating\":4.5,\"content\":\"다시 듣고 싶은 앨범\",\"isPrivate\":false,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\",\"isOwner\":true}}";
  static final String DELETE =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"음악 기록 삭제에 성공했습니다.\",\"data\":null}";
  static final String DETAIL_OWNER_PUBLIC =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"recordId\":1,\"authorId\":10,\"authorNickname\":\"?????\",\"authorProfileImageUrl\":null,\"music\":{\"musicId\":123,\"type\":\"ALBUM\",\"title\":\"Palette\",\"artistName\":\"IU\",\"coverImageUrl\":null},\"rating\":4.5,\"content\":\"??\",\"isPrivate\":false,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\",\"isOwner\":true}}";
  static final String DETAIL_OWNER_PRIVATE =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"recordId\":1,\"authorId\":10,\"authorNickname\":\"?????\",\"authorProfileImageUrl\":null,\"music\":{\"musicId\":123,\"type\":\"ALBUM\",\"title\":\"Palette\",\"artistName\":\"IU\",\"coverImageUrl\":null},\"rating\":4.5,\"content\":\"??\",\"isPrivate\":true,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\",\"isOwner\":true}}";
  static final String DETAIL_OTHER_RATING =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"recordId\":1,\"authorId\":10,\"authorNickname\":\"?????\",\"authorProfileImageUrl\":null,\"music\":{\"musicId\":123,\"type\":\"ALBUM\",\"title\":\"Palette\",\"artistName\":\"IU\",\"coverImageUrl\":null},\"rating\":4.5,\"content\":null,\"isPrivate\":false,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\",\"isOwner\":false}}";
  static final String DETAIL_OTHER_TEXT =
      "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"?? ?? ??? ??????.\",\"data\":{\"recordId\":1,\"authorId\":10,\"authorNickname\":null,\"authorProfileImageUrl\":null,\"music\":{\"musicId\":123,\"type\":\"ALBUM\",\"title\":\"Palette\",\"artistName\":\"IU\",\"coverImageUrl\":null},\"rating\":null,\"content\":\"??\",\"isPrivate\":false,\"createdAt\":\"2026-11-21T18:00:00\",\"updatedAt\":\"2026-11-21T18:00:00\",\"isOwner\":false}}";
}
