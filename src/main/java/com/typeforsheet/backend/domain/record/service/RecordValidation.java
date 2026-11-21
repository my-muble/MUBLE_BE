package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;

final class RecordValidation {
  private RecordValidation() {}

  static Byte rating(BigDecimal rating) {
    if (rating == null) return null;
    try {
      int value = rating.multiply(BigDecimal.valueOf(2)).intValueExact();
      if (value < 1 || value > 10) throw new ArithmeticException();
      return (byte) value;
    } catch (ArithmeticException invalid) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid);
    }
  }

  static String content(String content) {
    if (content != null
        && content
            .codePoints()
            .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) return null;
    if (content != null && content.codePointCount(0, content.length()) > 500)
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    return content;
  }

  static void requireContent(Byte rating, String content) {
    if (rating == null && content == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
  }
}
