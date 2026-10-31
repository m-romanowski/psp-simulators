package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripeSpecEvidence {

  private StripeSpecEvidence() {}

  static StripeJson schemasAt(String version) throws IOException {
    Map<String, StripeJson> schemas =
        new LinkedHashMap<>(
            object(member(StripeResponseConformanceTest.resource(version + ".json"), "schemas"))
                .members());
    StripeJson evidence = StripeResponseConformanceTest.resource("evidence-overrides.json");
    StripeJson latest =
        member(StripeResponseConformanceTest.resource("2026-10-28.endive.json"), "schemas");
    for (StripeJson patch : ((StripeJson.JsonArray) member(evidence, "fields")).elements()) {
      String scope = ((StripeJson.JsonText) member(patch, "scope")).value();
      if (scope.equals("mobile") && !version.startsWith("2020-")) {
        continue;
      }
      String name = ((StripeJson.JsonText) member(patch, "schema")).value();
      String field = ((StripeJson.JsonText) member(patch, "field")).value();
      Map<String, StripeJson> shape = new LinkedHashMap<>(object(schemas.get(name)).members());
      Map<String, StripeJson> properties =
          new LinkedHashMap<>(
              object(member(new StripeJson.JsonObject(shape), "properties")).members());
      StripeJson value = object(patch).members().get("shape");
      if (value == null) {
        value = member(member(member(latest, name), "properties"), field);
      }
      properties.put(field, value);
      shape.put("properties", new StripeJson.JsonObject(properties));
      schemas.put(name, new StripeJson.JsonObject(shape));
    }
    if (version.startsWith("2020-")) {
      for (String name :
          List.of(
              "payment_flows_automatic_payment_methods_payment_intent",
              "payment_flows_automatic_payment_methods_setup_intent",
              "payment_method_config_biz_payment_method_configuration_details")) {
        schemas.put(name, member(latest, name));
      }
    }
    return new StripeJson.JsonObject(schemas);
  }
}
