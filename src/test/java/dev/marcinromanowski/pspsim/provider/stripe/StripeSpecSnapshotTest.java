package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StripeSpecSnapshotTest {

  private static final StripeJson.JsonObject FULL =
      object(
          StripeJsonReader.read(
              """
      {
        "info":{"version":"2026-10-28.endive"},
        "paths":{"/v1/payment_methods":{"get":{"responses":{"200":{"content":{"application/json":{"schema":{"type":"object","properties":{"data":{"type":"array","items":{"$ref":"#/components/schemas/payment_method"}}}}}}}}}}},
        "components":{"schemas":{
          "payment_intent":{"type":"object","description":"discarded","required":["id"],"properties":{"target":{"$ref":"#/components/schemas/nested"}}},
          "setup_intent":{"type":"object"},"charge":{"type":"object"},"refund":{"type":"object"},
          "customer":{"type":"object"},"deleted_customer":{"type":"object"},"payment_method":{"type":"object"},
          "ephemeral_key":{"type":"object"},"event":{"type":"object"},"error":{"type":"object"},"api_errors":{"type":"object"},
          "nested":{"type":"object","x-expandableFields":["again"],"properties":{"again":{"$ref":"#/components/schemas/payment_intent"},"values":{"type":"array","items":{"type":"string","nullable":true,"enum":["card"],"x-stripeBypassValidation":true}}}},
          "unrelated":{"type":"string"}
        }}
      }
      """));

  private static final String COMMIT = "51610f5f061c9723730582a7638b8cc0e646640d";
  private static final String SOURCE =
      "https://raw.githubusercontent.com/stripe/openapi/51610f5f061c9723730582a7638b8cc0e646640d/latest/openapi.spec3.json";

  @Test
  void trimming_keeps_the_transitive_closure_and_validation_keys_in_stable_order() {
    String first = StripeSpecSnapshot.trimmed(FULL, "2026-10-28.endive", COMMIT, SOURCE);
    String second = StripeSpecSnapshot.trimmed(FULL, "2026-10-28.endive", COMMIT, SOURCE);
    assertThat(first)
        .isEqualTo(second)
        .endsWith("\n")
        .doesNotContain("discarded", "description", "required", "unrelated");
    StripeJson snapshot = StripeJsonReader.read(first);
    assertThat(object(member(snapshot, "schemas")).members().keySet())
        .containsExactly(
            "api_errors",
            "charge",
            "customer",
            "deleted_customer",
            "ephemeral_key",
            "error",
            "event",
            "nested",
            "payment_intent",
            "payment_method",
            "payment_method_list",
            "refund",
            "setup_intent");
    assertThat(member(member(member(snapshot, "schemas"), "nested"), "properties"))
        .isEqualTo(
            StripeJsonReader.read(
                """
            {"again":{"$ref":"#/components/schemas/payment_intent"},"values":{"type":"array","items":{"type":"string","nullable":true,"enum":["card"],"x-stripeBypassValidation":true}}}
            """));
    assertThat(member(snapshot, "provenance"))
        .isEqualTo(
            StripeJsonReader.read(
                """
        {"version":"2026-10-28.endive","commit":"51610f5f061c9723730582a7638b8cc0e646640d","source":"https://raw.githubusercontent.com/stripe/openapi/51610f5f061c9723730582a7638b8cc0e646640d/latest/openapi.spec3.json"}
        """));
  }

  @Test
  void a_mislabeled_version_or_mutable_commit_is_refused() {
    assertThatThrownBy(() -> StripeSpecSnapshot.trimmed(FULL, "2026-09-30.endive", COMMIT, SOURCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The source spec does not publish 2026-09-30.endive");
    assertThatThrownBy(
            () -> StripeSpecSnapshot.trimmed(FULL, "2026-10-28.endive", "master", SOURCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Provenance needs a full commit SHA");
  }
}
