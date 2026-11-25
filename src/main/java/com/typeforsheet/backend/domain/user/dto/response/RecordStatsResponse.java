package com.typeforsheet.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "마이페이지 전체 및 월별 기록 통계 응답")
public record RecordStatsResponse(
    @Schema(description = "전체 기록 수", example = "3") long totalRecordCount,
    @Schema(description = "앨범 기록 수", example = "2") long albumRecordCount,
    @Schema(description = "곡 기록 수", example = "1") long trackRecordCount,
    @Schema(description = "별점이 입력된 기록 수", example = "2") long ratedRecordCount,
    @Schema(description = "평균 별점. 별점 기록이 없으면 0", example = "3.75") BigDecimal averageRating,
    @Schema(description = "0.5점부터 5.0점까지의 별점 분포") List<RatingDistribution> ratingDistribution) {

  @Schema(description = "특정 별점의 기록 수")
  public record RatingDistribution(
      @Schema(description = "별점", example = "4.0") BigDecimal rating,
      @Schema(description = "해당 별점의 기록 수", example = "1") long count) {}
}
