package com.typeforsheet.backend.domain.user.controller;

import com.typeforsheet.backend.domain.user.dto.request.UpdateNicknameRequest;
import com.typeforsheet.backend.domain.user.dto.response.RecordStatsResponse;
import com.typeforsheet.backend.domain.user.dto.response.UserProfileResponse;
import com.typeforsheet.backend.domain.user.service.RecordStatsService;
import com.typeforsheet.backend.domain.user.service.UserService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지", description = "사용자 정보 및 기록 통계 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final UserService userService;
  private final RecordStatsService recordStatsService;

  @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 ID와 닉네임을 조회합니다. 닉네임 미설정 시 null을 반환합니다.")
  @GetMapping
  public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {

    Long userId = Long.valueOf(jwt.getSubject());

    UserProfileResponse profile = userService.getMyProfile(userId);

    return ApiResponse.success("내 정보 조회에 성공했습니다.", profile);
  }

  @Operation(summary = "닉네임 설정 및 수정", description = "닉네임을 설정하거나 수정합니다. 앞뒤 공백을 제거하며, 1~50자까지 허용합니다.")
  @PatchMapping("/nickname")
  public ApiResponse<UserProfileResponse> updateNickname(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateNicknameRequest request) {

    Long userId = Long.valueOf(jwt.getSubject());

    UserProfileResponse profile = userService.updateNickname(userId, request.nickname());

    return ApiResponse.success("닉네임 설정 및 수정에 성공했습니다.", profile);
  }

  @Operation(
      summary = "전체 및 월별 기록 통계 조회",
      description =
          "로그인한 사용자의 기록 통계를 조회합니다. month 생략 시 전체 기간, 입력 시 한국 시간 기준 해당 월의 최초 작성 기록을 집계하며 본인의 비공개 기록도 포함합니다.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "기록 통계 조회 성공",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples = {
                      @ExampleObject(
                          name = "전체 기간 통계",
                          summary = "month 미입력",
                          value =
                              """
                                {
                                  "success": true,
                                  "code": "SUCCESS",
                                  "message": "기록 통계 조회에 성공했습니다.",
                                  "data": {
                                    "totalRecordCount": 3,
                                    "albumRecordCount": 2,
                                    "trackRecordCount": 1,
                                    "ratedRecordCount": 2,
                                    "averageRating": 3.75,
                                    "ratingDistribution": [
                                      {"rating": 0.5, "count": 0},
                                      {"rating": 1.0, "count": 0},
                                      {"rating": 1.5, "count": 0},
                                      {"rating": 2.0, "count": 0},
                                      {"rating": 2.5, "count": 0},
                                      {"rating": 3.0, "count": 0},
                                      {"rating": 3.5, "count": 1},
                                      {"rating": 4.0, "count": 1},
                                      {"rating": 4.5, "count": 0},
                                      {"rating": 5.0, "count": 0}
                                    ]
                                  }
                                }
                                """),
                      @ExampleObject(
                          name = "월별 통계",
                          summary = "month=2026-10",
                          value =
                              """
                                {
                                  "success": true,
                                  "code": "SUCCESS",
                                  "message": "기록 통계 조회에 성공했습니다.",
                                  "data": {
                                    "totalRecordCount": 2,
                                    "albumRecordCount": 1,
                                    "trackRecordCount": 1,
                                    "ratedRecordCount": 1,
                                    "averageRating": 4.00,
                                    "ratingDistribution": [
                                      {"rating": 0.5, "count": 0},
                                      {"rating": 1.0, "count": 0},
                                      {"rating": 1.5, "count": 0},
                                      {"rating": 2.0, "count": 0},
                                      {"rating": 2.5, "count": 0},
                                      {"rating": 3.0, "count": 0},
                                      {"rating": 3.5, "count": 0},
                                      {"rating": 4.0, "count": 1},
                                      {"rating": 4.5, "count": 0},
                                      {"rating": 5.0, "count": 0}
                                    ]
                                  }
                                }
                                """)
                    })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 월 형식",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                """
                            {
                              "success": false,
                              "code": "INVALID_REQUEST",
                              "message": "잘못된 요청입니다.",
                              "data": null
                            }
                            """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                """
                            {
                              "success": false,
                              "code": "INVALID_ACCESS_TOKEN",
                              "message": "유효하지 않거나 만료된 Access Token입니다.",
                              "data": null
                            }
                            """)))
      })
  @GetMapping("/record-stats")
  public ApiResponse<RecordStatsResponse> getRecordStats(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "조회할 월 (YYYY-MM). 생략하면 전체 기간 조회", example = "2026-10")
          @RequestParam(name = "month", required = false)
          String month) {

    Long userId = Long.valueOf(jwt.getSubject());

    RecordStatsResponse stats = recordStatsService.getRecordStats(userId, month);

    return ApiResponse.success("기록 통계 조회에 성공했습니다.", stats);
  }
}
