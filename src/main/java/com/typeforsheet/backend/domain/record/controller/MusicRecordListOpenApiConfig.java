package com.typeforsheet.backend.domain.record.controller;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MusicRecordListOpenApiConfig {
  @Bean
  OpenApiCustomizer musicRecordListNullableCursor() {
    return api -> {
      if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
      api.getComponents()
          .getSchemas()
          .values()
          .forEach(MusicRecordListOpenApiConfig::makeCursorNullable);
    };
  }

  private static void makeCursorNullable(Schema<?> schema) {
    if (schema.getProperties() == null) return;
    Object property = schema.getProperties().get("nextCursor");
    if (!(property instanceof Schema<?> cursor)
        || !"#/components/schemas/MusicRecordListCursor".equals(cursor.get$ref())) return;
    ComposedSchema nullable = new ComposedSchema();
    nullable.setDescription(cursor.getDescription());
    nullable.addAnyOfItem(new Schema<>().$ref(cursor.get$ref()));
    Schema<?> nullSchema = new Schema<>();
    nullSchema.setTypes(Set.of("null"));
    nullable.addAnyOfItem(nullSchema);
    schema.getProperties().put("nextCursor", nullable);
  }
}
