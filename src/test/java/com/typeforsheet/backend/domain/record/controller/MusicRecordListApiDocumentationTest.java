package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typeforsheet.backend.domain.record.dto.MusicRecordListResponse;
import com.typeforsheet.backend.domain.record.service.MusicRecordListService;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.response.ApiResponse;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MusicRecordListApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean MusicRecordListService service;

  @Test
  void unexpectedFailureReturnsTheDocumentedServerError() throws Exception {
    when(service.getMine(anyLong(), anyLong(), anyInt(), isNull(), isNull()))
        .thenThrow(new DataAccessResourceFailureException("test failure"));
    var response =
        mvc.perform(
                get("/api/music/1/records/me")
                    .header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode error = mapper.readTree(response);
    assertEquals("INTERNAL_SERVER_ERROR", error.path("code").asText());
    assertErrorExample(error, ErrorCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  void documentsActualOperationsSchemasParametersAndExamples() throws Exception {
    JsonNode root =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertTrue(root.path("openapi").asText().startsWith("3.1"));
    JsonNode schemas = root.path("components").path("schemas");
    for (String scope : List.of("me", "others")) {
      JsonNode operation =
          root.path("paths").path("/api/music/{musicId}/records/" + scope).path("get");
      assertEquals(
          scope.equals("me") ? "음악별 나의 기록 목록 조회" : "음악별 타인의 기록 목록 조회",
          operation.path("summary").asText());
      assertTrue(operation.path("tags").toString().contains("음악 기록"));
      assertTrue(operation.path("description").asText().contains("totalRecordCount"));
      assertTrue(operation.path("description").asText().contains("%3A"));
      for (String heading : List.of("조회 대상", "정렬 기준", "조회 방식", "응답 및 유의사항", "프론트엔드 처리 사항"))
        assertTrue(operation.path("description").asText().contains(heading));
      assertTrue(operation.path("security").get(0).has("bearerAuth"));
      JsonNode musicId = parameter(operation, "musicId");
      assertEquals("path", musicId.path("in").asText());
      assertTrue(musicId.path("required").asBoolean());
      assertEquals("integer", musicId.path("schema").path("type").asText());
      assertEquals("int64", musicId.path("schema").path("format").asText());
      JsonNode limit = parameter(operation, "limit");
      assertFalse(limit.path("required").asBoolean());
      assertEquals("한 번의 요청에서 조회할 기록 수. 기본값 20개, 허용 범위 1~50개.", limit.path("description").asText());
      assertEquals("integer", limit.path("schema").path("type").asText());
      assertEquals(20, limit.path("schema").path("default").asInt());
      assertEquals(1, limit.path("schema").path("minimum").asInt());
      assertEquals(50, limit.path("schema").path("maximum").asInt());
      assertEquals(
          "local-date-time",
          parameter(operation, "cursorCreatedAt").path("schema").path("format").asText());
      assertEquals(
          "integer", parameter(operation, "cursorId").path("schema").path("type").asText());
      for (String name : List.of("cursorCreatedAt", "cursorId")) {
        JsonNode cursorParameter = parameter(operation, name);
        assertFalse(cursorParameter.path("required").asBoolean());
        assertFalse(cursorParameter.has("example"));
        assertFalse(cursorParameter.has("examples"));
        assertFalse(cursorParameter.path("schema").has("example"));
        assertFalse(cursorParameter.path("schema").has("examples"));
        assertFalse(cursorParameter.path("schema").has("default"));
      }
      for (String code : List.of("200", "400", "401", "404", "500"))
        assertTrue(operation.path("responses").has(code), scope + " " + code);
      JsonNode response =
          operation.path("responses").path("200").path("content").path("application/json");
      JsonNode wrapper = resolve(schemas, response.path("schema"));
      JsonNode page = resolve(schemas, wrapper.path("properties").path("data"));
      assertTrue(page.path("properties").has("results"), page.toString());
      assertTrue(page.path("properties").has("totalRecordCount"));
      JsonNode item = resolve(schemas, page.path("properties").path("results").path("items"));
      assertTrue(item.path("properties").has("recordId"));
      assertTrue(item.path("properties").path("rating").path("type").toString().contains("null"));
      if (scope.equals("me")) {
        assertTrue(
            item.path("properties").path("content").path("type").toString().contains("null"));
        assertTrue(item.path("properties").has("isPrivate"));
      } else {
        assertFalse(item.path("properties").has("isPrivate"));
        assertTrue(
            item.path("properties")
                .path("authorNickname")
                .path("type")
                .toString()
                .contains("null"));
        assertTrue(
            item.path("properties")
                .path("authorProfileImageUrl")
                .path("type")
                .toString()
                .contains("null"));
        assertEquals("string", item.path("properties").path("content").path("type").asText());
      }
      JsonNode cursor = page.path("properties").path("nextCursor");
      JsonNode alternatives = cursor.path("anyOf");
      assertEquals(2, alternatives.size(), cursor.toString());
      assertEquals("null", alternatives.get(1).path("type").asText(), cursor.toString());
      assertTrue(
          resolve(schemas, alternatives.get(0)).path("properties").has("recordId"),
          cursor.toString());
      JsonNode examples = response.path("examples");
      assertEquals(4, examples.size());
      for (JsonNode example : examples) validateExample(example.path("value"), scope);
      JsonNode first =
          examples
              .path(scope.equals("me") ? "첫 페이지 (limit=3)" : "첫 페이지 (limit=2)")
              .path("value")
              .path("data");
      assertEquals(scope.equals("me") ? 3 : 2, first.path("results").size());
      assertTrue(first.path("hasNext").asBoolean());
      JsonNode lastPage =
          examples
              .path(scope.equals("me") ? "마지막 페이지 (limit=3)" : "마지막 페이지 (limit=2)")
              .path("value")
              .path("data");
      assertEquals(scope.equals("me") ? 5 : 3, lastPage.path("totalRecordCount").asInt());
      assertEquals(scope.equals("me") ? 2 : 1, lastPage.path("results").size());
      assertFalse(lastPage.path("hasNext").asBoolean());
      JsonNode afterCursor = examples.path("커서 이후 결과 없음").path("value").path("data");
      assertTrue(afterCursor.path("results").isEmpty());
      assertTrue(afterCursor.path("totalRecordCount").asInt() > 0);
      assertFalse(afterCursor.path("hasNext").asBoolean());
      assertTrue(afterCursor.path("nextCursor").isNull());
      assertEquals(
          "INVALID_REQUEST",
          operation
              .path("responses")
              .path("400")
              .path("content")
              .path("application/json")
              .path("example")
              .path("code")
              .asText());
      assertEquals(
          "INVALID_ACCESS_TOKEN",
          operation
              .path("responses")
              .path("401")
              .path("content")
              .path("application/json")
              .path("example")
              .path("code")
              .asText());
      assertEquals(
          "INTERNAL_SERVER_ERROR",
          operation
              .path("responses")
              .path("500")
              .path("content")
              .path("application/json")
              .path("example")
              .path("code")
              .asText());
      JsonNode missing =
          operation
              .path("responses")
              .path("404")
              .path("content")
              .path("application/json")
              .path("examples");
      assertEquals(
          "USER_NOT_FOUND", missing.path("USER_NOT_FOUND").path("value").path("code").asText());
      assertEquals(
          "MUSIC_ITEM_NOT_FOUND",
          missing.path("MUSIC_ITEM_NOT_FOUND").path("value").path("code").asText());
      for (String code : List.of("400", "401", "500")) {
        JsonNode error =
            operation
                .path("responses")
                .path(code)
                .path("content")
                .path("application/json")
                .path("example");
        ErrorCode errorCode = ErrorCode.valueOf(error.path("code").asText());
        assertEquals(Integer.parseInt(code), errorCode.getStatus().value());
        assertErrorExample(error, errorCode);
      }
      assertErrorExample(missing.path("USER_NOT_FOUND").path("value"), ErrorCode.USER_NOT_FOUND);
      assertErrorExample(
          missing.path("MUSIC_ITEM_NOT_FOUND").path("value"), ErrorCode.MUSIC_ITEM_NOT_FOUND);
    }
  }

  private void assertErrorExample(JsonNode error, ErrorCode code) {
    assertFalse(error.path("success").asBoolean());
    assertEquals(code.getMessage(), error.path("message").asText());
    assertTrue(error.has("data"));
    assertTrue(error.path("data").isNull());
  }

  private void validateExample(JsonNode value, String scope) {
    assertTrue(value.path("success").asBoolean());
    assertEquals("SUCCESS", value.path("code").asText());
    assertEquals(
        scope.equals("me") ? "나의 음악 기록 목록 조회에 성공했습니다." : "타인의 음악 기록 목록 조회에 성공했습니다.",
        value.path("message").asText());
    JsonNode data = value.path("data");
    JsonNode results = data.path("results");
    if (data.path("hasNext").asBoolean()) {
      assertTrue(data.path("totalRecordCount").asLong() > results.size());
      JsonNode last = results.get(results.size() - 1);
      assertEquals(last.path("recordId"), data.path("nextCursor").path("recordId"));
      assertEquals(last.path("createdAt"), data.path("nextCursor").path("createdAt"));
    } else assertTrue(data.path("nextCursor").isNull());
    for (JsonNode item : results) {
      assertTrue(item.has("rating"));
      assertTrue(item.has("content"));
      LocalDateTime.parse(item.path("createdAt").asText());
      if (scope.equals("others")) {
        assertTrue(item.has("authorNickname"));
        assertTrue(item.path("authorProfileImageUrl").isNull());
      }
    }
    Object sample =
        scope.equals("me")
            ? new MusicRecordListResponse.Mine(
                1L, null, null, false, LocalDateTime.of(2026, 1, 1, 0, 0))
            : new MusicRecordListResponse.Other(
                1L, 2L, null, null, null, "text", LocalDateTime.of(2026, 1, 1, 0, 0));
    JsonNode actual =
        mapper.readTree(
            mapper.writeValueAsString(
                ApiResponse.success(
                    value.path("message").asText(),
                    new MusicRecordListResponse<>(List.of(sample), 1, false, null))));
    assertEquals(
        actual.properties().stream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet()),
        value.properties().stream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet()));
    if (!results.isEmpty())
      assertEquals(
          actual.path("data").path("results").get(0).properties().stream()
              .map(java.util.Map.Entry::getKey)
              .collect(java.util.stream.Collectors.toSet()),
          results.get(0).properties().stream()
              .map(java.util.Map.Entry::getKey)
              .collect(java.util.stream.Collectors.toSet()));
  }

  private JsonNode parameter(JsonNode operation, String name) {
    for (JsonNode param : operation.path("parameters"))
      if (name.equals(param.path("name").asText())) return param;
    fail("Missing parameter " + name);
    return null;
  }

  private JsonNode resolve(JsonNode schemas, JsonNode schema) {
    String ref = schema.path("$ref").asText();
    return ref.isEmpty() ? schema : schemas.path(ref.substring(ref.lastIndexOf('/') + 1));
  }
}
