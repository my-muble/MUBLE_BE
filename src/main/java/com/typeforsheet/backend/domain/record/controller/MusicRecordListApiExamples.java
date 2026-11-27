package com.typeforsheet.backend.domain.record.controller;

final class MusicRecordListApiExamples {
  private MusicRecordListApiExamples() {}

  static final String MINE_FIRST =
      """
      {"success":true,"code":"SUCCESS","message":"나의 음악 기록 목록 조회에 성공했습니다.","data":{
        "results":[
          {"recordId":105,"rating":4.5,"content":"다시 듣고 싶은 앨범","isPrivate":false,"createdAt":"2026-11-25T10:00:00.123456"},
          {"recordId":104,"rating":3.0,"content":null,"isPrivate":true,"createdAt":"2026-11-24T10:00:00"},
          {"recordId":103,"rating":null,"content":"좋은 음악","isPrivate":false,"createdAt":"2026-11-23T10:00:00"}
        ],"totalRecordCount":5,"hasNext":true,"nextCursor":{"createdAt":"2026-11-23T10:00:00","recordId":103}}}
      """;
  static final String MINE_LAST =
      """
      {"success":true,"code":"SUCCESS","message":"나의 음악 기록 목록 조회에 성공했습니다.","data":{
        "results":[
          {"recordId":102,"rating":4.0,"content":null,"isPrivate":false,"createdAt":"2026-11-22T10:00:00"},
          {"recordId":101,"rating":null,"content":"기억에 남는 곡","isPrivate":true,"createdAt":"2026-11-21T10:00:00"}
        ],"totalRecordCount":5,"hasNext":false,"nextCursor":null}}
      """;
  static final String MINE_EMPTY =
      """
      {"success":true,"code":"SUCCESS","message":"나의 음악 기록 목록 조회에 성공했습니다.","data":{"results":[],"totalRecordCount":0,"hasNext":false,"nextCursor":null}}
      """;
  static final String MINE_AFTER_CURSOR =
      """
      {"success":true,"code":"SUCCESS","message":"나의 음악 기록 목록 조회에 성공했습니다.","data":{"results":[],"totalRecordCount":5,"hasNext":false,"nextCursor":null}}
      """;
  static final String OTHERS_FIRST =
      """
      {"success":true,"code":"SUCCESS","message":"타인의 음악 기록 목록 조회에 성공했습니다.","data":{
        "results":[
          {"recordId":203,"authorId":10,"authorNickname":"민지","authorProfileImageUrl":null,"rating":4.5,"content":"다시 듣고 싶어요","createdAt":"2026-11-25T10:00:00.123456"},
          {"recordId":202,"authorId":11,"authorNickname":null,"authorProfileImageUrl":null,"rating":null,"content":"좋은 음악","createdAt":"2026-11-24T10:00:00"}
        ],"totalRecordCount":3,"hasNext":true,"nextCursor":{"createdAt":"2026-11-24T10:00:00","recordId":202}}}
      """;
  static final String OTHERS_LAST =
      """
      {"success":true,"code":"SUCCESS","message":"타인의 음악 기록 목록 조회에 성공했습니다.","data":{
        "results":[{"recordId":201,"authorId":12,"authorNickname":"서연","authorProfileImageUrl":null,"rating":null,"content":"기억에 남아요","createdAt":"2026-11-23T10:00:00"}],
        "totalRecordCount":3,"hasNext":false,"nextCursor":null}}
      """;
  static final String OTHERS_EMPTY =
      """
      {"success":true,"code":"SUCCESS","message":"타인의 음악 기록 목록 조회에 성공했습니다.","data":{"results":[],"totalRecordCount":0,"hasNext":false,"nextCursor":null}}
      """;
  static final String OTHERS_AFTER_CURSOR =
      """
      {"success":true,"code":"SUCCESS","message":"타인의 음악 기록 목록 조회에 성공했습니다.","data":{"results":[],"totalRecordCount":3,"hasNext":false,"nextCursor":null}}
      """;
}
