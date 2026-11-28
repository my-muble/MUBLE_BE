package com.typeforsheet.backend.domain.archive.dto;

import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.util.Locale;

public enum ArchiveAlbumSort {
  LATEST,
  MOST_RECORDED;

  public static ArchiveAlbumSort parse(String value) {
    if (value == null || value.isBlank()) {
      return LATEST;
    }
    String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    if (normalized.equals("RECORD_COUNT") || normalized.equals("MOST_RECORDS")) {
      return MOST_RECORDED;
    }
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }
}
