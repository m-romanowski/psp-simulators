package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.OpenApiConvention.Answer;
import dev.marcinromanowski.pspsim.documentation.OpenApiConvention.Finding;
import dev.marcinromanowski.pspsim.documentation.OpenApiConvention.Published;
import dev.marcinromanowski.pspsim.documentation.OpenApiConvention.Shape;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenApiConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final Path COMPILED_TESTS = Path.of("build", "classes", "java", "test");

  private static final String FIXTURES =
      "dev.marcinromanowski.pspsim.documentation.OpenApiConventionTest$";

  private static final String HOLDING = FIXTURES + "Holding";

  private static final String LEG = FIXTURES + "Leg";

  private static final ObjectMapper JSON = new ObjectMapper();

  enum Outcome {
    PAID,
    DECLINED
  }

  record Leg(int sequence, String kind) {}

  record Holding(
      String providerObjectId,
      @Nullable String amount,
      boolean capturesAccepted,
      Instant updatedAt,
      Outcome outcome,
      List<Leg> operations,
      Map<String, List<String>> headers,
      @Nullable Leg newest) {}

  private JsonSchemaOfARecord schemas() {
    return new JsonSchemaOfARecord(new CompiledRecords(COMPILED_TESTS), COMPILED_TESTS);
  }

  @Test
  void a_published_schema_lists_every_component_of_the_record_and_nothing_else() {
    JsonSchemaOfARecord schemas = schemas();
    schemas.referenceTo(HOLDING);

    ObjectNode holding = schemas.schemasDefined().get("Holding");

    assertThat(names(holding.get("properties")))
        .containsExactly(
            "amount",
            "capturesAccepted",
            "headers",
            "newest",
            "operations",
            "outcome",
            "providerObjectId",
            "updatedAt");
    assertThat(holding.get("additionalProperties").booleanValue()).isFalse();
  }

  @Test
  void a_component_that_may_arrive_null_is_typed_to_allow_null_and_is_not_required() {
    JsonSchemaOfARecord schemas = schemas();
    schemas.referenceTo(HOLDING);
    ObjectNode holding = schemas.schemasDefined().get("Holding");

    assertThat(names(holding.get("required")))
        .containsExactly(
            "capturesAccepted",
            "headers",
            "operations",
            "outcome",
            "providerObjectId",
            "updatedAt");
    assertThat(holding.get("properties").get("amount").get("type").toString())
        .isEqualTo("[\"string\",\"null\"]");
    assertThat(
            holding.get("properties").get("newest").get("anyOf").get(1).get("type").stringValue())
        .isEqualTo("null");
  }

  @Test
  void every_shape_a_record_component_can_carry_is_read_out_of_its_compiled_type() {
    JsonSchemaOfARecord schemas = schemas();
    schemas.referenceTo(HOLDING);
    ObjectNode holding = schemas.schemasDefined().get("Holding");
    JsonNode properties = holding.get("properties");

    assertThat(properties.get("capturesAccepted").get("type").stringValue()).isEqualTo("boolean");
    assertThat(properties.get("updatedAt").get("format").stringValue()).isEqualTo("date-time");
    assertThat(names(properties.get("outcome").get("enum"))).containsExactly("DECLINED", "PAID");
    assertThat(properties.get("operations").get("items").get("$ref").stringValue())
        .isEqualTo("#/components/schemas/Leg");
    assertThat(properties.get("headers").get("additionalProperties").get("type").stringValue())
        .isEqualTo("array");
    assertThat(schemas.schemasDefined()).containsKey("Leg");
    assertThat(names(schemas.schemasDefined().get("Leg").get("properties")))
        .containsExactly("kind", "sequence");
  }

  @Test
  void a_shape_no_compiled_type_describes_is_refused_rather_than_invented() {
    JsonSchemaOfARecord schemas = schemas();

    assertThatThrownBy(
            () ->
                schemas.referenceTo(
                    "dev.marcinromanowski.pspsim.documentation.OpenApiConventionTest"))
        .isInstanceOf(JsonSchemaOfARecord.UnpublishableShape.class)
        .hasMessageContaining("no compiled record of that name");
  }

  @Test
  void two_records_that_want_one_schema_name_are_refused() {
    JsonSchemaOfARecord schemas = schemas();
    schemas.referenceTo(LEG);

    assertThatThrownBy(() -> schemas.schemaNameOf("dev.marcinromanowski.pspsim.elsewhere.Leg"))
        .isInstanceOf(JsonSchemaOfARecord.UnpublishableShape.class)
        .hasMessageContaining("both want to be published as the schema named Leg");
  }

  @Test
  void a_controller_in_a_package_no_document_covers_is_refused() {
    assertThatThrownBy(
            () ->
                RoutesInTheBuild.familyOf(
                    "dev.marcinromanowski.pspsim.elsewhere.SomethingController"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("neither dev.marcinromanowski.pspsim.control");

    assertThat(
            RoutesInTheBuild.familyOf(
                "dev.marcinromanowski.pspsim.control.SimulatorControlController"))
        .isEqualTo(RoutesInTheBuild.CONTROL);
    assertThat(
            RoutesInTheBuild.familyOf(
                "dev.marcinromanowski.pspsim.provider.grow.GrowLightApiController"))
        .isEqualTo(RoutesInTheBuild.PROVIDER + "grow");
    assertThat(RoutesInTheBuild.familyOf("dev.marcinromanowski.pspsim.ui.DashboardController"))
        .isEqualTo(RoutesInTheBuild.SCREENS);
  }

  @Test
  void the_routes_of_this_build_are_read_out_of_the_mapping_annotations() {
    List<RoutesInTheBuild.Route> routes = RoutesInTheBuild.under(COMPILED_MAIN);

    RoutesInTheBuild.Route reset = one(routes, "POST /__simulator/{provider}/reset");
    assertThat(reset.operationId()).isEqualTo("reset");
    assertThat(reset.family()).isEqualTo(RoutesInTheBuild.CONTROL);
    assertThat(reset.requestBodyClass())
        .contains("dev.marcinromanowski.pspsim.kernel.spi.DeclaredReset");
    assertThat(reset.requestBodyRequired()).isFalse();
    assertThat(reset.parameters())
        .containsExactly(new RoutesInTheBuild.Parameter("provider", "path", true));

    RoutesInTheBuild.Route audit = one(routes, "GET /__simulator/{provider}/audit");
    assertThat(audit.parameters())
        .contains(new RoutesInTheBuild.Parameter("testRunId", "query", false));

    RoutesInTheBuild.Route refund = one(routes, "POST /api/light/server/1.0/refundTransaction");
    assertThat(refund.family()).isEqualTo(RoutesInTheBuild.PROVIDER + "grow");
    assertThat(refund.requestBodyClass()).isEmpty();
    assertThat(refund.bodyArrivesUnparsed()).isTrue();
  }

  @Test
  void a_control_route_bound_to_no_record_is_named_and_the_view_is_not_published() {
    Published published =
        OpenApiConvention.build(COMPILED_MAIN, Map.of(), List.of(), "1.2.3", "0.0.1");

    assertThat(messages(published.findings()))
        .anyMatch(
            message ->
                message.contains("POST /__simulator/{provider}/reset")
                    && message.contains("binds it to no record"));
  }

  @Test
  void a_binding_naming_a_route_this_build_does_not_serve_is_named() {
    Published published =
        OpenApiConvention.build(
            COMPILED_MAIN,
            Map.of(
                "POST /__simulator/{provider}/retired",
                new Answer(
                    Shape.OBJECT,
                    "dev.marcinromanowski.pspsim.control.ControlPayloads$ResetResponse")),
            List.of(),
            "1.2.3",
            "0.0.1");

    assertThat(messages(published.findings()))
        .anyMatch(
            message ->
                message.contains("POST /__simulator/{provider}/retired")
                    && message.contains("no control route of this build answers on it"));
  }

  @Test
  void the_committed_control_document_names_every_control_route_of_this_build() {
    JsonNode control = JSON.readTree(readString(committed(OpenApiConvention.CONTROL_DOCUMENT)));
    TreeSet<String> documented = new TreeSet<>();
    for (Map.Entry<String, JsonNode> path : control.get("paths").properties()) {
      for (String method : names(path.getValue())) {
        documented.add(method.toUpperCase(Locale.ROOT) + " " + path.getKey());
      }
    }
    TreeSet<String> compiled = new TreeSet<>();
    for (RoutesInTheBuild.Route route : RoutesInTheBuild.under(COMPILED_MAIN)) {
      if (route.family().equals(RoutesInTheBuild.CONTROL)) {
        compiled.add(route.signature());
      }
    }

    assertThat(compiled).isNotEmpty();
    assertThat(documented).isEqualTo(compiled);
  }

  @Test
  void the_committed_view_states_shapes_and_never_a_value() {
    List<String> checked = new ArrayList<>();
    for (Path document : committedDocuments()) {
      String published = readString(document);
      checked.add(document.getFileName().toString());
      assertThat(published)
          .withFailMessage(document + " carries a value where the view states only shapes")
          .doesNotContain("\"example\"")
          .doesNotContain("\"examples\"")
          .doesNotContain("\"const\"");
    }

    assertThat(checked).contains(OpenApiConvention.CONTROL_DOCUMENT, "grow.json", "stripe.json");
  }

  @Test
  void a_published_example_anywhere_in_the_view_is_refused() {
    JsonNode carrying =
        JSON.readTree(
            """
            { "components": { "schemas": { "Account": { "properties": {
              "userId": { "type": "string", "example": "sk-live-9f3" } } } } } }
            """);
    List<Finding> findings = new ArrayList<>();

    OpenApiConvention.forbidValuesIn("control.json", carrying, findings);

    assertThat(messages(findings))
        .anyMatch(
            message ->
                message.contains("Account.properties.userId.example")
                    && message.contains("Name the field and publish no value for it"));
  }

  @Test
  void a_provider_route_names_its_contract_document_as_a_plain_reference() {
    Published published =
        OpenApiConvention.build(COMPILED_MAIN, Map.of(), List.of(), "1.2.3", "0.0.1");

    assertThat(published.files().get("grow.json"))
        .contains("specified in doc/grow.md.")
        .doesNotContain("doc/grow.md, \"");
    assertThat(messages(published.findings())).noneMatch(message -> message.contains("doc/"));
  }

  @Test
  void a_generic_type_is_read_to_its_innermost_argument() {
    JsonSchemaOfARecord.Descriptors.JavaType read =
        new JsonSchemaOfARecord.Descriptors(
                "Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/String;>;>;")
            .read();

    assertThat(read).isInstanceOf(JsonSchemaOfARecord.Descriptors.Parameterized.class);
    JsonSchemaOfARecord.Descriptors.Parameterized keyed =
        (JsonSchemaOfARecord.Descriptors.Parameterized) read;
    assertThat(keyed.binaryName()).isEqualTo("java.util.Map");
    assertThat(keyed.arguments()).hasSize(2);
    assertThat(
            ((JsonSchemaOfARecord.Descriptors.Parameterized) keyed.arguments().get(1)).binaryName())
        .isEqualTo("java.util.List");
  }

  private static Path committed(String name) {
    return Path.of("").toAbsolutePath().resolve(OpenApiConvention.PUBLISHED_UNDER).resolve(name);
  }

  private static List<Path> committedDocuments() {
    try (var listed = Files.list(committed(""))) {
      return listed
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String readString(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static RoutesInTheBuild.Route one(List<RoutesInTheBuild.Route> routes, String signature) {
    for (RoutesInTheBuild.Route route : routes) {
      if (route.signature().equals(signature)) {
        return route;
      }
    }
    throw new IllegalStateException(
        signature + " is served by no route this build compiled; it reads " + routes.size());
  }

  private static List<String> names(JsonNode node) {
    TreeSet<String> sorted = new TreeSet<>();
    if (node.isArray()) {
      node.forEach(element -> sorted.add(element.stringValue()));
    } else {
      node.propertyNames().forEach(sorted::add);
    }
    return List.copyOf(sorted);
  }

  private static List<String> messages(List<Finding> findings) {
    return findings.stream().map(Finding::message).toList();
  }
}
