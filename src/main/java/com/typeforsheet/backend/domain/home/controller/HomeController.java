package com.typeforsheet.backend.domain.home.controller;

import com.typeforsheet.backend.domain.home.dto.response.HomePublicRecordResponse;
import com.typeforsheet.backend.domain.home.service.HomePublicRecordService;
import com.typeforsheet.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "홈 화면 공개 기록 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
@SecurityRequirement(name = "bearerAuth")
public class HomeController {

  private final HomePublicRecordService publicRecordService;

  @Operation(
      summary = "타인 공개 기록 조회",
      description =
          "로그인한 사용자를 제외한 타인의 공개 기록을 최초 작성일 최신순으로 최대 10개 조회합니다. 앨범과 곡을 모두 포함하며, 본문과 별점은 미입력 시 null입니다.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "타인 공개 기록 조회 성공",
            useReturnTypeSchema = true,
            content =
                @Content(
                    mediaType = "application/json",
                    examples = {
                      @ExampleObject(
                          name = "공개 기록 조회",
                          summary = "타인의 공개 기록이 있는 경우",
                          value =
                              """
                                {
                                  "success": true,
                                  "code": "SUCCESS",
                                  "message": "타인 공개 기록 조회에 성공했습니다.",
                                  "data": [
                                    {
                                      "recordId": 303,
                                      "authorNickname": "민지",
                                      "music": {
                                        "musicId": 123,
                                        "type": "ALBUM",
                                        "title": "Palette",
                                        "artistName": "IU",
                                        "coverImageUrl": "https://example.com/album-cover.jpg"
                                      },
                                      "rating": 4.5,
                                      "contentPreview": null,
                                      "createdAt": "2026-11-25T10:40:00"
                                    },
                                    {
                                      "recordId": 302,
                                      "authorNickname": "서연",
                                      "music": {
                                        "musicId": 456,
                                        "type": "TRACK",
                                        "title": "밤편지",
                                        "artistName": "IU",
                                        "coverImageUrl": null
                                      },
                                      "rating": null,
                                      "contentPreview": "가사가 정말 마음에 드는 곡!",
                                      "createdAt": "2026-11-25T10:30:00"
                                    },
                                    {
                                      "recordId": 301,
                                      "authorNickname": "하늘",
                                      "music": {
                                        "musicId": 123,
                                        "type": "ALBUM",
                                        "title": "Palette",
                                        "artistName": "IU",
                                        "coverImageUrl": "https://example.com/album-cover.jpg"
                                      },
                                      "rating": 4.0,
                                      "contentPreview": "오랜만에 다시 들었는데 좋았어.",
                                      "createdAt": "2026-11-24T18:20:00"
                                    }
                                  ]
                                }
                                """),
                      @ExampleObject(
                          name = "공개 기록 없음",
                          summary = "조회할 공개 기록이 없는 경우",
                          value =
                              """
                                {
                                  "success": true,
                                  "code": "SUCCESS",
                                  "message": "타인 공개 기록 조회에 성공했습니다.",
                                  "data": []
                                }
                                """)
                    })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            name = "유효하지 않은 Access Token",
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
  @GetMapping("/public-records")
  public ApiResponse<List<HomePublicRecordResponse>> getPublicRecords(
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = Long.valueOf(jwt.getSubject());

    List<HomePublicRecordResponse> records = publicRecordService.getPublicRecords(userId);

    return ApiResponse.success("타인 공개 기록 조회에 성공했습니다.", records);
  }
}
