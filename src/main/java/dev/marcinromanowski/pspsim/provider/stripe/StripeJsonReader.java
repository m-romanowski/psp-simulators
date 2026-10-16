package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

final class StripeJsonReader {

  private static final ObjectMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .build();

  private StripeJsonReader() {}

  static StripeJson read(String written) {
    return of(JSON.readTree(written));
  }

  private static StripeJson of(JsonNode node) {
    return switch (node.getNodeType()) {
      case OBJECT -> objectOf(node);
      case ARRAY -> arrayOf(node);
      case STRING -> StripeJson.text(node.stringValue());
      case NUMBER -> StripeJson.number(node.toString());
      case BOOLEAN -> StripeJson.bool(node.booleanValue());
      default -> StripeJson.nothing();
    };
  }

  private static StripeJson objectOf(JsonNode node) {
    Map<String, StripeJson> members = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> member : node.properties()) {
      members.put(member.getKey(), of(member.getValue()));
    }
    return new StripeJson.JsonObject(members);
  }

  private static StripeJson arrayOf(JsonNode node) {
    List<StripeJson> elements = new ArrayList<>();
    for (JsonNode element : node.values()) {
      elements.add(of(element));
    }
    return StripeJson.array(elements);
  }
}
