package com.typeforsheet.backend.domain.record.controller;

import com.typeforsheet.backend.domain.record.dto.MusicRecordListResponse;
import com.typeforsheet.backend.domain.record.service.MusicRecordListService;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music/{musicId}/records")
@RequiredArgsConstructor
public class MusicRecordListController {
  private final MusicRecordListService service;

  @GetMapping("/me")
  public ApiResponse<MusicRecordListResponse<MusicRecordListResponse.Mine>> getMine(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String musicId,
      @RequestParam(required = false) String limit,
      @RequestParam(required = false) String cursorCreatedAt,
      @RequestParam(required = false) String cursorId) {
    return ApiResponse.success(
        "나의 음악 기록 목록 조회에 성공했습니다.",
        service.getMine(
            Long.valueOf(jwt.getSubject()),
            number(musicId),
            limit(limit),
            date(cursorCreatedAt),
            optionalId(cursorId)));
  }

  @GetMapping("/others")
  public ApiResponse<MusicRecordListResponse<MusicRecordListResponse.Other>> getOthers(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String musicId,
      @RequestParam(required = false) String limit,
      @RequestParam(required = false) String cursorCreatedAt,
      @RequestParam(required = false) String cursorId) {
    return ApiResponse.success(
        "타인의 음악 기록 목록 조회에 성공했습니다.",
        service.getOthers(
            Long.valueOf(jwt.getSubject()),
            number(musicId),
            limit(limit),
            date(cursorCreatedAt),
            optionalId(cursorId)));
  }

  private long number(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private Long optionalId(String value) {
    return value == null ? null : number(value);
  }

  private int limit(String value) {
    if (value == null) return 20;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private LocalDateTime date(String value) {
    if (value == null) return null;
    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }
}
