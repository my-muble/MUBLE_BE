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
    boolean keyRequired = false;
    for (JsonNode parameter : create.path("parameters")) {
      if ("Idempotency-Key".equals(parameter.path("name").asText()))
        keyRequired = parameter.path("required").asBoolean();
    }
    assertTrue(keyRequired);
    JsonNode schemas = root.path("components").path("schemas");
    assertTrue(schemas.path("CreateRecordRequest").path("properties").has("isPrivate"));
    JsonNode patchProperties = schemas.path("UpdateRecordRequest").path("properties");
    assertTrue(patchProperties.has("rating"));
    assertTrue(patchProperties.has("content"));
    assertTrue(patchProperties.has("isPrivate"));
    assertFalse(patchProperties.has("musicId"));
  }
}
