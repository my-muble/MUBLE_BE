package com.typeforsheet.backend.domain.user.service;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.user.dto.response.RecordStatsResponse;
import com.typeforsheet.backend.domain.user.dto.response.RecordStatsResponse.RatingDistribution;
import com.typeforsheet.backend.domain.user.repository.RecordStatsRepository;
import com.typeforsheet.backend.domain.user.repository.RecordStatsRow;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordStatsService {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

  private static final int RATING_BUCKET_COUNT = 10;

  private final RecordStatsRepository recordStatsRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public RecordStatsResponse getRecordStats(Long userId, String month) {

    YearMonth selectedMonth = month == null ? null : parseMonth(month);

    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    List<RecordStatsRow> rows;

    if (selectedMonth == null) {

      rows = recordStatsRepository.findRecordStatsByUserId(userId);

    } else {

      LocalDateTime startInclusive;
      LocalDateTime endExclusive;

      try {

        ZoneId storageZone = ZoneId.systemDefault();

        startInclusive =
            selectedMonth
                .atDay(1)
                .atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(storageZone)
                .toLocalDateTime();

        endExclusive =
            selectedMonth
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(storageZone)
                .toLocalDateTime();

      } catch (DateTimeException exception) {

        throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
      }

      rows =
          recordStatsRepository.findRecordStatsByUserIdAndPeriod(
              userId, startInclusive, endExclusive);
    }

    return calculateStats(rows);
  }

  private YearMonth parseMonth(String month) {

    if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    try {

      YearMonth parsedMonth = YearMonth.parse(month);

      if (parsedMonth.getYear() < 1) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
      }

      return parsedMonth;

    } catch (DateTimeException exception) {

      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private RecordStatsResponse calculateStats(List<RecordStatsRow> rows) {

    long totalRecordCount = 0;
    long albumRecordCount = 0;
    long trackRecordCount = 0;
    long ratedRecordCount = 0;
    long weightedRatingSum = 0;

    long[] ratingCounts = new long[RATING_BUCKET_COUNT];

    for (RecordStatsRow row : rows) {

      long count = row.recordCount();

      totalRecordCount += count;

      if (row.musicType() == MusicType.ALBUM) {

        albumRecordCount += count;

      } else if (row.musicType() == MusicType.TRACK) {

        trackRecordCount += count;
      }

      Byte ratingHalfSteps = row.ratingHalfSteps();

      if (ratingHalfSteps == null) {
        continue;
      }

      int ratingIndex = ratingHalfSteps.intValue() - 1;

      if (ratingIndex < 0 || ratingIndex >= RATING_BUCKET_COUNT) {

        throw new IllegalStateException("저장된 별점이 허용 범위를 벗어났습니다.");
      }

      ratedRecordCount += count;

      ratingCounts[ratingIndex] += count;

      weightedRatingSum += (long) ratingHalfSteps * count;
    }

    BigDecimal averageRating;

    if (ratedRecordCount == 0) {

      averageRating = BigDecimal.ZERO;

    } else {

      averageRating =
          BigDecimal.valueOf(weightedRatingSum)
              .divide(
                  BigDecimal.valueOf(ratedRecordCount).multiply(BigDecimal.valueOf(2)),
                  2,
                  RoundingMode.HALF_UP);
    }

    List<RatingDistribution> ratingDistribution = new ArrayList<>();

    for (int i = 0; i < RATING_BUCKET_COUNT; i++) {

      BigDecimal rating =
          BigDecimal.valueOf(i + 1).divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);

      ratingDistribution.add(new RatingDistribution(rating, ratingCounts[i]));
    }

    return new RecordStatsResponse(
        totalRecordCount,
        albumRecordCount,
        trackRecordCount,
        ratedRecordCount,
        averageRating,
        List.copyOf(ratingDistribution));
  }
}
