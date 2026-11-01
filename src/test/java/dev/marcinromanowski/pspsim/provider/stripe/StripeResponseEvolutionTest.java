package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeResponseEvolutionTest {

  @Test
  void an_undeclared_type_change_makes_the_snapshot_guard_fail() {
    StripeResponseEvolution guard = new StripeResponseEvolution(List.of());
    assertThat(
            guard.undeclared(
                snapshot(
                    "2026-08-26.dahlia",
                    """
        {"sample":{"properties":{"amount":{"type":"integer"}}}}
        """),
                snapshot(
                    "2026-09-30.endive",
                    """
        {"sample":{"properties":{"amount":{"type":"string"}}}}
        """)))
        .containsExactly(
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.amount", "type_changed"));
  }

  @Test
  void enum_narrowing_and_field_removal_require_matching_change_data() {
    StripeResponseEvolution.Snapshot older =
        snapshot(
            "2026-08-26.dahlia",
            """
        {"sample":{"properties":{"status":{"type":"string","enum":["pending","succeeded"]},"old_name":{"type":"string"}}}}
        """);
    StripeResponseEvolution.Snapshot newer =
        snapshot(
            "2026-09-30.endive",
            """
        {"sample":{"properties":{"status":{"type":"string","enum":["succeeded"]}}}}
        """);
    assertThat(new StripeResponseEvolution(List.of()).undeclared(older, newer))
        .containsExactlyInAnyOrder(
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.status", "enum_narrowed"),
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.old_name", "removed"));
    List<StripeResponseEvolution.BreakingChange> changes =
        List.of(
            new StripeResponseEvolution.BreakingChange(
                "2026-09-30.endive",
                "sample.status",
                "enum_narrowed",
                "",
                "",
                "https://docs.stripe.com/changelog/endive/2026-09-30/example"),
            new StripeResponseEvolution.BreakingChange(
                "2026-09-30.endive",
                "sample.old_name",
                "removed",
                "",
                "",
                "https://docs.stripe.com/changelog/endive/2026-09-30/example"));
    assertThat(new StripeResponseEvolution(changes).undeclared(older, newer)).isEmpty();
  }

  @Test
  void changed_references_unions_and_narrowed_nullability_are_not_silent() {
    StripeResponseEvolution guard = new StripeResponseEvolution(List.of());
    assertThat(
            guard.undeclared(
                snapshot(
                    "2026-08-26.dahlia",
                    """
        {"sample":{"properties":{"target":{"$ref":"#/components/schemas/old"},"nullable":{"type":"string","nullable":true},"union":{"anyOf":[{"type":"string"}]}}}}
        """),
                snapshot(
                    "2026-09-30.endive",
                    """
        {"sample":{"properties":{"target":{"$ref":"#/components/schemas/new"},"nullable":{"type":"string"},"union":{"anyOf":[{"type":"string"},{"type":"integer"}]}}}}
        """)))
        .containsExactlyInAnyOrder(
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.target", "type_changed"),
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.nullable", "nullability_narrowed"),
            new StripeResponseEvolution.Difference(
                "2026-09-30.endive", "sample.union", "type_changed"));
  }

  @Test
  void additive_fields_use_the_first_definition_and_enum_additions_are_shared() {
    StripeResponseEvolution evolution = new StripeResponseEvolution(List.of());
    List<StripeResponseEvolution.Snapshot> snapshots =
        List.of(
            snapshot(
                "2025-03-31.basil",
                """
            {"sample":{"properties":{"status":{"type":"string","enum":["pending"]}}}}
            """),
            snapshot(
                "2026-07-29.dahlia",
                """
            {"sample":{"properties":{"status":{"type":"string","enum":["pending","succeeded"]},"added":{"type":"integer"}}}}
            """),
            snapshot(
                "2026-09-30.endive",
                """
            {"sample":{"properties":{"status":{"type":"string","enum":["pending","succeeded"]},"added":{"type":"string"}}}}
            """));
    StripeJson schema = evolution.at("2025-03-31.basil", snapshots);
    assertThat(member(member(member(schema, "sample"), "properties"), "added"))
        .isEqualTo(StripeJsonReader.read("{\"type\":\"integer\"}"));
    assertThat(
            new StripeResponseSchema(schema)
                .problems(
                    "sample", StripeJsonReader.read("{\"added\":3,\"status\":\"succeeded\"}")))
        .isEmpty();
    assertThat(
            new StripeResponseSchema(schema)
                .problems("sample", StripeJsonReader.read("{\"added\":\"3\"}")))
        .containsExactly(new StripeResponseSchema.Problem("sample.added", "type:integer"));
    assertThat(evolution.undeclared(snapshots.getFirst(), snapshots.get(1))).isEmpty();
  }

  @Test
  void explicit_breaking_values_stay_strict_even_when_stripe_bypasses_the_rest_of_the_enum() {
    List<StripeResponseEvolution.Snapshot> snapshots =
        List.of(
            snapshot(
                "2025-03-31.basil",
                """
        {"sample":{"properties":{"reason":{"type":"string","enum":["automatic"],"x-stripeBypassValidation":true}}}}
        """),
            snapshot(
                "2026-09-30.endive",
                """
        {"sample":{"properties":{"reason":{"type":"string","enum":["automatic","expired"],"x-stripeBypassValidation":true}}}}
        """));
    StripeResponseEvolution evolution =
        new StripeResponseEvolution(
            List.of(
                new StripeResponseEvolution.BreakingChange(
                    "2026-09-30.endive",
                    "sample.reason",
                    "enum_replacement",
                    "automatic",
                    "expired",
                    "https://docs.stripe.com/changelog/endive/2026-09-30/example")));
    StripeResponseSchema schema =
        new StripeResponseSchema(evolution.at("2025-03-31.basil", snapshots));
    assertThat(schema.problems("sample", StripeJsonReader.read("{\"reason\":\"expired\"}")))
        .containsExactly(new StripeResponseSchema.Problem("sample.reason", "breaking-enum"));
    assertThat(
            schema.problems(
                "sample", StripeJsonReader.read("{\"reason\":\"future_additive_value\"}")))
        .isEmpty();
  }

  @Test
  void a_declared_break_blocks_additive_fields_across_its_boundary() {
    List<StripeResponseEvolution.Snapshot> snapshots =
        List.of(
            snapshot(
                "2025-03-31.basil",
                """
        {"sample":{"properties":{}}}
        """),
            snapshot(
                "2026-09-30.endive",
                """
        {"sample":{"properties":{"changed":{"type":"string"}}}}
        """));
    StripeResponseEvolution evolution =
        new StripeResponseEvolution(
            List.of(
                new StripeResponseEvolution.BreakingChange(
                    "2026-09-30.endive",
                    "sample.changed",
                    "type_changed",
                    "",
                    "",
                    "https://docs.stripe.com/changelog/endive/2026-09-30/example")));
    assertThat(
            new StripeResponseSchema(evolution.at("2025-03-31.basil", snapshots))
                .problems("sample", StripeJsonReader.read("{\"changed\":\"value\"}")))
        .containsExactly(new StripeResponseSchema.Problem("sample.changed", "unpublished"));
  }

  @Test
  void consecutive_answered_snapshots_have_no_undeclared_break_on_the_rendered_surface()
      throws IOException {
    StripeJson omissions =
        member(StripeResponseConformanceTest.resource("omissions.json"), "always");
    StripeResponseEvolution evolution =
        new StripeResponseEvolution(
            StripeResponseEvolution.changes(
                StripeResponseConformanceTest.resource("breaking-changes.json")));
    List<StripeResponseEvolution.Difference> differences = new ArrayList<>();
    StripeResponseEvolution.Snapshot previous = null;
    for (StripeResponseEvolution.Snapshot snapshot : StripeSpecEvidence.snapshots()) {
      if (snapshot.version().startsWith("2020-")) {
        continue;
      }
      StripeResponseEvolution.Snapshot surface =
          new StripeResponseEvolution.Snapshot(
              snapshot.version(), renderedSurface(snapshot.schemas(), omissions));
      if (previous != null) {
        differences.addAll(evolution.undeclared(previous, surface));
      }
      previous = surface;
    }
    assertThat(differences).isEmpty();
  }

  @Test
  void breaking_change_data_matches_the_declared_runtime_change_and_its_changelog()
      throws IOException {
    assertThat(
            StripeResponseEvolution.changes(
                StripeResponseConformanceTest.resource("breaking-changes.json")))
        .containsExactly(
            new StripeResponseEvolution.BreakingChange(
                "2025-03-31.basil",
                "payment_intent.cancellation_reason",
                "enum_replacement",
                "automatic",
                "expired",
                "https://docs.stripe.com/changelog/basil/2025-03-31/remove-refund-from-partial-capture-and-payment-cancellation-flow"));
    assertThat(StripeVersionChanges.AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED.changelog())
        .isEqualTo(
            "https://docs.stripe.com/changelog/basil/2025-03-31/remove-refund-from-partial-capture-and-payment-cancellation-flow");
  }

  private static StripeJson renderedSurface(StripeJson schemas, StripeJson omissions) {
    Map<String, StripeJson> surface = new LinkedHashMap<>();
    object(omissions)
        .members()
        .forEach(
            (name, omitted) -> {
              StripeJson schema = object(schemas).members().get(name);
              if (schema != null) {
                Map<String, StripeJson> fields =
                    new LinkedHashMap<>(object(member(schema, "properties")).members());
                object(omitted).members().keySet().forEach(fields::remove);
                Map<String, StripeJson> shape = new LinkedHashMap<>(object(schema).members());
                shape.put("properties", new StripeJson.JsonObject(fields));
                surface.put(name, new StripeJson.JsonObject(shape));
              }
            });
    return new StripeJson.JsonObject(surface);
  }

  private static StripeResponseEvolution.Snapshot snapshot(String version, String schemas) {
    return new StripeResponseEvolution.Snapshot(version, StripeJsonReader.read(schemas));
  }
}
