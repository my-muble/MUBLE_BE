package com.typeforsheet.backend.domain.record.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(
    description = "생략된 필드는 유지하며, 별점과 본문은 null로 제거할 수 있습니다.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class UpdateRecordRequest {
  @Schema(description = "0.5~5.0, 0.5 단위. null이면 별점 제거", nullable = true)
  private BigDecimal rating;

  @Schema(description = "최대 500자. null 또는 공백만 입력하면 본문 제거", nullable = true)
  private String content;

  @Schema(description = "나만 보기 여부. null 불가")
  private Boolean isPrivate;

  private boolean ratingPresent;
  private boolean contentPresent;
  private boolean privatePresent;

  @Schema(
      description = "0.5~5.0, 0.5 단위. null이면 별점 제거",
      types = {"number", "null"},
      minimum = "0.5",
      maximum = "5.0",
      multipleOf = 0.5)
  @JsonSetter("rating")
  public void setRating(BigDecimal value) {
    ratingPresent = true;
    rating = value;
  }

  @Schema(
      description = "최대 500자. null 또는 공백만 입력하면 본문 제거",
      types = {"string", "null"},
      maxLength = 500)
  @JsonSetter("content")
  public void setContent(String value) {
    contentPresent = true;
    content = value;
  }

  @Schema(description = "나만 보기 여부. 생략할 수 있지만 null 불가")
  @JsonSetter("isPrivate")
  public void setPrivate(Boolean value) {
    privatePresent = true;
    isPrivate = value;
  }

  @JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field: " + name);
  }

  public boolean ratingPresent() {
    return ratingPresent;
  }

  public boolean contentPresent() {
    return contentPresent;
  }

  public boolean privatePresent() {
    return privatePresent;
  }

  public BigDecimal rating() {
    return rating;
  }

  public String content() {
    return content;
  }

  public Boolean isPrivate() {
    return isPrivate;
  }
}
