package com.typeforsheet.backend.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RecordApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;

  @Test
  void documentsAuthenticationFieldsIdempotencyAndErrorResponses() throws Exception {
    JsonNode root =
        mapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertTrue(root.path("openapi").asText().startsWith("3.1"));
    JsonNode paths = root.path("paths");
    JsonNode create = paths.path("/api/records").path("post");
    JsonNode item = paths.path("/api/records/{recordId}");
    for (JsonNode operation :
        new JsonNode[] {create, item.path("get"), item.path("patch"), item.path("delete")}) {
      assertTrue(operation.path("security").get(0).has("bearerAuth"));
      assertTrue(operation.path("responses").has("200"));
      assertTrue(operation.path("responses").has("400"));
      assertTrue(operation.path("responses").has("401"));
      assertTrue(operation.path("responses").has("404"));
      assertTrue(operation.path("responses").has("500"));
      String errorSchema =
          operation
              .path("responses")
              .path("400")
              .path("content")
              .path("application/json")
              .path("schema")
              .path("$ref")
              .asText();
      assertTrue(
          errorSchema.endsWith("RecordErrorResponse"), "Error response schema: " + errorSchema);
    }
    assertTrue(item.path("patch").path("responses").has("403"));
    assertTrue(item.path("delete").path("responses").has("403"));
    assertTrue(create.path("responses").has("409"));
    assertTrue(
        create
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText()
            .endsWith("RecordWriteSuccess"));
    assertTrue(
        item.path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText()
            .endsWith("RecordDetailSuccess"));
    assertTrue(
        item.path("delete")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("example")
            .path("data")
            .isNull());
    JsonNode conflicts =
        create
            .path("responses")
            .path("409")
            .path("content")
            .path("application/json")
            .path("examples");
    for (String code : new String[] {"IDEMPOTENCY_KEY_CONFLICT", "RECORD_ALREADY_DELETED"}) {
      assertEquals(code, conflicts.path(code).path("value").path("code").asText());
      assertTrue(conflicts.path(code).path("value").path("data").isNull());
    }
    for (String method : new String[] {"get", "patch", "delete"}) {
      JsonNode id = item.path(method).path("parameters").get(0).path("schema");
      assertEquals("integer", id.path("type").asText());
      assertEquals("int64", id.path("format").asText());
    }
    boolean keyRequired = false;
    for (JsonNode parameter : create.path("parameters")) {
      if ("Idempotency-Key".equals(parameter.path("name").asText()))
        keyRequired = parameter.path("required").asBoolean();
    }
    assertTrue(keyRequired);
    JsonNode schemas = root.path("components").path("schemas");
    JsonNode detail = schemas.path("RecordDetailResponse");
    for (String name : new String[] {"authorNickname", "authorProfileImageUrl"}) {
      assertNullType(detail.path("properties").path(name), "string");
      assertTrue(detail.path("required").toString().contains(name));
    }
    JsonNode detailExamples =
        item.path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("examples");
    assertEquals(5, detailExamples.size());
    detailExamples
        .properties()
        .forEach(
            entry -> {
              JsonNode data = entry.getValue().path("value").path("data");
              assertTrue(data.has("authorNickname"));
              assertTrue(data.has("authorProfileImageUrl"));
              assertTrue(data.path("authorProfileImageUrl").isNull());
            });
    assertTrue(schemas.path("CreateRecordRequest").path("properties").has("isPrivate"));
    JsonNode patchProperties = schemas.path("UpdateRecordRequest").path("properties");
    assertTrue(patchProperties.has("rating"));
    assertTrue(patchProperties.has("content"));
    assertTrue(patchProperties.has("isPrivate"));
    assertFalse(patchProperties.has("musicId"));
    for (String name : new String[] {"rating", "content", "isPrivate"})
      assertFalse(patchProperties.path(name).path("description").asText().isBlank());
    assertNullType(schemas.path("CreateRecordRequest").path("properties").path("rating"), "number");
    assertNullType(
        schemas.path("CreateRecordRequest").path("properties").path("content"), "string");
    assertNullType(patchProperties.path("rating"), "number");
    assertNullType(patchProperties.path("content"), "string");
    assertEquals("boolean", patchProperties.path("isPrivate").path("type").asText());
    for (String name : new String[] {"CreateRecordResponse", "RecordDetailResponse"}) {
      assertNullType(schemas.path(name).path("properties").path("rating"), "number");
      assertNullType(schemas.path(name).path("properties").path("content"), "string");
      assertTrue(schemas.path(name).path("required").toString().contains("createdAt"));
    }
    for (String name : new String[] {"title", "artistName", "coverImageUrl"})
      assertNullType(schemas.path("Music").path("properties").path(name), "string");
    assertNullOnly(schemas.path("RecordErrorResponse").path("properties").path("data"));
    assertNullOnly(schemas.path("RecordDeleteResponseSchema").path("properties").path("data"));
    JsonNode rating = schemas.path("CreateRecordRequest").path("properties").path("rating");
    assertEquals(0.5, rating.path("minimum").asDouble());
    assertEquals(5.0, rating.path("maximum").asDouble());
    assertEquals(0.5, rating.path("multipleOf").asDouble());
    JsonNode requestExample =
        create.path("requestBody").path("content").path("application/json").path("example");
    assertTrue(requestExample.path("rating").isNumber());
    assertEquals(4.5, requestExample.path("rating").asDouble());
    for (JsonNode operation :
        new JsonNode[] {create, item.path("get"), item.path("patch"), item.path("delete")}) {
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
    }
  }

  private void assertNullType(JsonNode schema, String other) {
    assertTrue(schema.path("type").isArray(), schema.toString());
    assertTrue(schema.path("type").toString().contains("\"null\""), schema.toString());
    assertTrue(schema.path("type").toString().contains("\"" + other + "\""), schema.toString());
  }

  private void assertNullOnly(JsonNode schema) {
    assertEquals("null", schema.path("type").asText(), schema.toString());
    assertFalse(schema.path("writeOnly").asBoolean());
  }
}
