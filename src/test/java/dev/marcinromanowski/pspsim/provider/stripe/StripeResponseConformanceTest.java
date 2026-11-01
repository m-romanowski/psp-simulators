package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StripeResponseConformanceTest {

  static Stream<String> versions() throws IOException {
    return ((StripeJson.JsonArray) resource("versions.json"))
        .elements().stream().map(value -> ((StripeJson.JsonText) value).value());
  }

  @ParameterizedTest
  @MethodSource("versions")
  void every_rendered_variant_conforms_to_its_published_response_schema(String version)
      throws IOException {
    StripeResponseSchema schema = new StripeResponseSchema(StripeSpecEvidence.schemasAt(version));
    Set<String> failures = new TreeSet<>();
    boolean mobile = version.startsWith("2020-");
    for (StripeConformanceSamples.Sample sample : StripeConformanceSamples.all(mobile)) {
      for (String operation : sample.operations()) {
        StripeJson answer = sample.at(version, operation);
        List<StripeResponseSchema.Problem> problems =
            new ArrayList<>(schema.problems(sample.schema(), answer));
        if (sample.schema().equals("event")) {
          StripeJson data = member(member(answer, "data"), "object");
          problems.addAll(
              schema.problems(((StripeJson.JsonText) member(data, "object")).value(), data));
        }
        for (StripeResponseSchema.Problem problem : problems) {
          failures.add(sample.schema() + " " + problem.path() + " " + problem.rule());
        }
      }
    }
    assertThat(failures).as(version).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("versions")
  void every_deliberate_omission_is_published_and_no_omitted_field_is_emitted(String version)
      throws IOException {
    StripeJson schemas = StripeSpecEvidence.schemasAt(version);
    StripeJson omissions = resource("omissions.json");
    boolean mobile = version.startsWith("2020-");
    StripeResponseSchema validator = new StripeResponseSchema(schemas);
    for (StripeConformanceSamples.Sample sample : StripeConformanceSamples.all(mobile)) {
      StripeJson answer = sample.at(version, sample.operations().getFirst());
      validator.problems(sample.schema(), answer);
    }
    validator
        .emitted()
        .forEach(
            (name, fields) -> {
              Set<String> published =
                  object(member(member(schemas, name), "properties")).members().keySet();
              Set<String> missing = new TreeSet<>(published);
              missing.removeAll(fields);
              Map<String, StripeJson> declared =
                  new HashMap<>(object(member(member(omissions, "always"), name)).members());
              if (mobile) {
                declared.putAll(object(member(member(omissions, "mobile"), name)).members());
              }
              declared.forEach(
                  (field, reason) ->
                      assertThat(((StripeJson.JsonText) reason).value())
                          .as(name + "." + field)
                          .isNotBlank());
              Set<String> applicable = new TreeSet<>(declared.keySet());
              applicable.retainAll(published);
              assertThat(missing).as(version + " omissions of " + name).isEqualTo(applicable);
            });
  }

  @Test
  void snapshots_cover_every_answered_version_and_the_mobile_pins_with_immutable_provenance()
      throws IOException {
    List<String> expected = new ArrayList<>();
    StripeApiVersions.answered().forEach(version -> expected.add(version.wireValue()));
    StripeApiVersions.PINNED_BY_THE_MOBILE_SDKS.forEach(
        version -> expected.add(version.wireValue()));
    assertThat(versions().toList()).containsExactlyInAnyOrderElementsOf(expected);
    for (String version : versions().toList()) {
      StripeJson provenance = member(resource(version + ".json"), "provenance");
      assertThat(member(provenance, "version")).isEqualTo(StripeJson.text(version));
      String commit = ((StripeJson.JsonText) member(provenance, "commit")).value();
      assertThat(commit).matches("[0-9a-f]{40}");
      assertThat(((StripeJson.JsonText) member(provenance, "source")).value())
          .startsWith("https://raw.githubusercontent.com/stripe/openapi/" + commit + "/")
          .endsWith(".json");
    }
  }

  @Test
  void an_omission_cannot_survive_after_its_field_disappears_from_every_snapshot()
      throws IOException {
    StripeJson omissions = resource("omissions.json");
    Map<String, Set<String>> published = new HashMap<>();
    Map<String, Set<String>> mobilePublished = new HashMap<>();
    for (String version : versions().toList()) {
      StripeJson schemas = StripeSpecEvidence.schemasAt(version);
      collectPublished(schemas, member(omissions, "always"), published);
      if (version.startsWith("2020-")) {
        collectPublished(schemas, member(omissions, "mobile"), mobilePublished);
      }
    }
    for (String scope : List.of("always", "mobile")) {
      object(member(omissions, scope))
          .members()
          .forEach(
              (name, fields) ->
                  assertThat(
                          (scope.equals("mobile") ? mobilePublished : published)
                              .getOrDefault(name, Set.of()))
                      .as(scope + " " + name)
                      .containsAll(object(fields).members().keySet()));
    }
  }

  private static void collectPublished(
      StripeJson schemas, StripeJson omissions, Map<String, Set<String>> into) {
    object(omissions)
        .members()
        .keySet()
        .forEach(
            name -> {
              StripeJson schema = object(schemas).members().get(name);
              if (schema != null) {
                into.computeIfAbsent(name, absent -> new HashSet<>())
                    .addAll(object(member(schema, "properties")).members().keySet());
              }
            });
  }

  static StripeJson resource(String filename) throws IOException {
    try (InputStream input =
        Objects.requireNonNull(
            StripeResponseConformanceTest.class.getResourceAsStream("/stripe-openapi/" + filename),
            filename)) {
      return StripeJsonReader.read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
