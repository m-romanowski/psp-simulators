package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BrowsableApiIntegrationTest extends PostgresBackedTest {

  private static final String THE_RUN = "openapi-view-it";

  private static final String PROVIDER = "grow";

  private static final String CONTROL = "/__simulator/" + PROVIDER;

  private static final Pattern ASSET =
      Pattern.compile("/webjars/swagger-ui/([0-9][0-9A-Za-z.\\-]*)/swagger-ui\\.css");

  private static final ObjectMapper JSON = new ObjectMapper();

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
  }

  @AfterEach
  void forgetWhatThisTestConfigured() {
    send(HttpMethod.POST, CONTROL + "/reset", Map.of("testRunId", THE_RUN, "scope", "TEST_RUN"));
  }

  @Test
  void the_api_view_is_a_screen_a_tester_can_open_and_its_assets_are_served() {
    ResponseEntity<String> page = get("/openapi/" + OpenApiConvention.PAGE);

    assertThat(page.getStatusCode().value()).isEqualTo(200);
    assertThat(page.getBody()).contains("SwaggerUIBundle").contains("index.json");

    Matcher asset = ASSET.matcher(String.valueOf(page.getBody()));
    assertThat(asset.find())
        .withFailMessage("the page loads no swagger-ui stylesheet from a webjar")
        .isTrue();
    String version = asset.group(1);
    for (String served :
        List.of("swagger-ui.css", "swagger-ui-bundle.js", "swagger-ui-standalone-preset.js")) {
      ResponseEntity<String> resource = get("/webjars/swagger-ui/" + version + "/" + served);
      assertThat(resource.getStatusCode().value())
          .withFailMessage("the page asks for " + served + " and the simulator does not serve it")
          .isEqualTo(200);
      assertThat(String.valueOf(resource.getBody())).isNotEmpty();
    }
  }

  @Test
  void every_document_the_page_offers_is_served_and_names_the_routes_of_its_half() {
    JsonNode listed = JSON.readTree(String.valueOf(get("/openapi/index.json").getBody()));

    assertThat(listed.isArray()).isTrue();
    assertThat(listed.size()).isGreaterThanOrEqualTo(6);
    Set<String> titles = new TreeSet<>();
    for (JsonNode document : listed) {
      ResponseEntity<String> served = get("/openapi/" + document.get("url").stringValue());
      assertThat(served.getStatusCode().value()).isEqualTo(200);
      JsonNode api = JSON.readTree(String.valueOf(served.getBody()));
      assertThat(api.get("openapi").stringValue()).isEqualTo(OpenApiConvention.OPENAPI);
      assertThat(api.get("paths").size()).isPositive();
      titles.add(api.get("info").get("title").stringValue());
    }
    assertThat(titles).contains("PSP simulator control API", "PSP simulator provider grow");
  }

  @Test
  void a_provider_route_publishes_no_shape_and_names_the_document_that_specifies_it() {
    JsonNode grow = JSON.readTree(String.valueOf(get("/openapi/grow.json").getBody()));
    JsonNode refund = grow.get("paths").get("/api/light/server/1.0/refundTransaction").get("post");

    assertThat(refund.get("description").stringValue())
        .contains("doc/grow.md")
        .contains("no schema is published for it here");
    JsonNode answered = refund.get("responses").get("default");
    assertThat(answered.get("description").stringValue()).contains("doc/grow.md");
    assertThat(answered.get("content").get("*/*").get("schema").properties()).isEmpty();
  }

  @Test
  void the_screens_carry_a_link_to_the_api_view() {
    ResponseEntity<String> workbench = get("/ui?provider=" + PROVIDER + "&testRunId=" + THE_RUN);

    assertThat(workbench.getStatusCode().value()).isEqualTo(200);
    assertThat(String.valueOf(workbench.getBody())).contains("/openapi/index.html");
  }

  @Test
  void what_the_published_control_document_promises_is_what_every_route_it_names_answers() {
    JsonNode control = JSON.readTree(String.valueOf(get("/openapi/control.json").getBody()));
    List<String> walked = new ArrayList<>();
    int objectsCompared = 0;

    objectsCompared += held(control, walked, HttpMethod.PUT, "/config", configuration());
    objectsCompared += held(control, walked, HttpMethod.GET, "/address", null);
    objectsCompared += held(control, walked, HttpMethod.GET, "/operations", null);
    objectsCompared += held(control, walked, HttpMethod.GET, "/personas", null);
    objectsCompared += held(control, walked, HttpMethod.GET, "/idempotency", null);

    JsonNode rule = answered(HttpMethod.POST, CONTROL + "/scenarios", scenario());
    String scenarioId = rule.get("scenarioId").stringValue();
    objectsCompared += held(control, walked, HttpMethod.POST, "/scenarios", scenario());
    objectsCompared += held(control, walked, HttpMethod.GET, "/scenarios", null);
    objectsCompared +=
        heldAt(
            control,
            walked,
            HttpMethod.GET,
            "/scenarios/{scenarioId}",
            "/scenarios/" + scenarioId,
            null);

    JsonNode seeded =
        answered(HttpMethod.POST, CONTROL + "/fixtures/payment-methods", fixture("visa-walked"));
    String fixtureId = seeded.get("fixtureId").stringValue();
    objectsCompared += held(control, walked, HttpMethod.GET, "/fixtures/payment-methods", null);
    objectsCompared +=
        held(
            control,
            walked,
            HttpMethod.POST,
            "/fixtures/payment-methods",
            fixture("visa-published"));
    objectsCompared += held(control, walked, HttpMethod.POST, "/clock/advance", advance());
    objectsCompared += held(control, walked, HttpMethod.GET, "/run-bundle", null);
    objectsCompared += held(control, walked, HttpMethod.GET, "/audit", null);
    objectsCompared +=
        held(
            control,
            walked,
            HttpMethod.POST,
            "/webhooks/deliver-due",
            Map.of("testRunId", THE_RUN));
    objectsCompared +=
        heldAt(
            control,
            walked,
            HttpMethod.DELETE,
            "/fixtures/payment-methods/{fixtureId}",
            "/fixtures/payment-methods/" + fixtureId,
            null);
    objectsCompared +=
        heldAt(
            control,
            walked,
            HttpMethod.DELETE,
            "/scenarios/{scenarioId}",
            "/scenarios/" + scenarioId,
            null);
    objectsCompared +=
        held(control, walked, HttpMethod.POST, "/reset", Map.of("testRunId", THE_RUN));

    assertThat(new LinkedHashSet<>(walked)).hasSize(walked.size());
    assertThat(walked).hasSizeGreaterThanOrEqualTo(17);
    assertThat(objectsCompared)
        .withFailMessage(
            "this test compared "
                + objectsCompared
                + " published objects against the wire, which is too few for it to be proving that"
                + " the published control document is true")
        .isGreaterThanOrEqualTo(50);
  }

  private int held(
      JsonNode control,
      List<String> walked,
      HttpMethod method,
      String path,
      @Nullable Object body) {
    return heldAt(control, walked, method, path, path, body);
  }

  private int heldAt(
      JsonNode control,
      List<String> walked,
      HttpMethod method,
      String published,
      String called,
      @Nullable Object body) {
    String template = "/__simulator/{provider}" + published;
    JsonNode operation =
        control.get("paths").get(template).get(method.name().toLowerCase(Locale.ROOT));
    assertThat(operation)
        .withFailMessage("the published control document names no " + method + " " + template)
        .isNotNull();
    JsonNode schema =
        operation.get("responses").get("2XX").get("content").get("application/json").get("schema");
    JsonNode answered =
        answered(method, CONTROL + called + (body == null ? "?testRunId=" + THE_RUN : ""), body);
    walked.add(method + " " + template);
    return compared(control, schema, answered, method + " " + template);
  }

  private int compared(JsonNode control, JsonNode schema, JsonNode body, String where) {
    JsonNode resolved = resolve(control, schema);
    if ("array".equals(text(resolved.get("type")))) {
      assertThat(body.isArray())
          .withFailMessage(where + " is published as an array and answered something else")
          .isTrue();
      int compared = 0;
      for (JsonNode element : body) {
        compared += compared(control, resolved.get("items"), element, where + "[]");
      }
      return compared;
    }
    if (!resolved.has("properties")) {
      return 0;
    }
    assertThat(body.isObject())
        .withFailMessage(where + " is published as an object and answered something else")
        .isTrue();
    Set<String> published = new TreeSet<>();
    resolved.get("properties").propertyNames().forEach(published::add);
    Set<String> onTheWire = new TreeSet<>();
    body.propertyNames().forEach(onTheWire::add);
    assertThat(onTheWire)
        .withFailMessage(
            where
                + " publishes the fields "
                + published
                + " and answers "
                + onTheWire
                + ", so a consumer coding against the published document reads a field that never"
                + " arrives or misses one that does")
        .isEqualTo(published);
    int compared = 1;
    for (String field : published) {
      JsonNode value = body.get(field);
      JsonNode asPublished = resolved.get("properties").get(field);
      JsonNode declared = resolve(control, asPublished);
      if (value.isNull()) {
        assertThat(allowsNull(asPublished))
            .withFailMessage(
                where + "." + field + " arrived null and the published document forbids null there")
            .isTrue();
        continue;
      }
      if (value.isObject() && declared.has("properties")) {
        compared += compared(control, declared, value, where + "." + field);
      }
      if (value.isArray() && declared.has("items")) {
        for (JsonNode element : value) {
          if (element.isObject()) {
            compared +=
                compared(control, declared.get("items"), element, where + "." + field + "[]");
          }
        }
      }
      if (value.isObject() && declared.has("additionalProperties")) {
        JsonNode keyed = resolve(control, declared.get("additionalProperties"));
        if (keyed.has("properties")) {
          for (Map.Entry<String, JsonNode> property : value.properties()) {
            compared += compared(control, keyed, property.getValue(), where + "." + field + ".*");
          }
        }
      }
    }
    return compared;
  }

  private static boolean allowsNull(JsonNode schema) {
    JsonNode type = schema.get("type");
    if (type != null && type.isArray()) {
      for (JsonNode allowed : type) {
        if (JsonSchemaOfARecord.NULL.equals(allowed.stringValue())) {
          return true;
        }
      }
    }
    JsonNode either = schema.get("anyOf");
    if (either != null) {
      for (JsonNode option : either) {
        if (JsonSchemaOfARecord.NULL.equals(text(option.get("type")))) {
          return true;
        }
      }
    }
    return false;
  }

  private static JsonNode resolve(JsonNode control, JsonNode schema) {
    JsonNode reference = schema.get("$ref");
    if (reference == null) {
      JsonNode either = schema.get("anyOf");
      if (either == null) {
        return schema;
      }
      for (JsonNode option : either) {
        if (option.has("$ref")) {
          return resolve(control, option);
        }
      }
      return schema;
    }
    String name = reference.stringValue().substring("#/components/schemas/".length());
    return control.get("components").get("schemas").get(name);
  }

  private static @Nullable String text(@Nullable JsonNode node) {
    return node == null || !node.isString() ? null : node.stringValue();
  }

  private JsonNode answered(HttpMethod method, String path, @Nullable Object body) {
    ResponseEntity<String> response = send(method, path, body);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .withFailMessage(
            method
                + " "
                + path
                + " is published as answering a body on success and answered "
                + response.getStatusCode()
                + ": "
                + response.getBody())
        .isTrue();
    return JSON.readTree(String.valueOf(response.getBody()));
  }

  private ResponseEntity<String> send(HttpMethod method, String path, @Nullable Object body) {
    RestClient.RequestBodySpec request =
        http.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON);
      return request
          .body(body)
          .retrieve()
          .onStatus(status -> true, (i, o) -> {})
          .toEntity(String.class);
    }
    return request.retrieve().onStatus(status -> true, (i, o) -> {}).toEntity(String.class);
  }

  private ResponseEntity<String> get(String path) {
    return http.get()
        .uri(path)
        .retrieve()
        .onStatus(status -> true, (i, o) -> {})
        .toEntity(String.class);
  }

  private static Map<String, Object> configuration() {
    return Map.of(
        "testRunId",
        THE_RUN,
        "clock",
        Map.of("fixedNow", "2026-09-07T12:00:00Z"),
        "accounts",
        List.of(
            Map.of(
                "accountId",
                "merchant-1",
                "configuration",
                Map.of("userId", "openapi-user", "pageCode", "openapi-page"))));
  }

  private static Map<String, Object> scenario() {
    return Map.of(
        "testRunId",
        THE_RUN,
        "priority",
        100,
        "match",
        Map.of("merchantId", "merchant-1"),
        "action",
        Map.of("type", "FIXED_SUCCESS"));
  }

  private static Map<String, Object> fixture(String alias) {
    return Map.of(
        "testRunId",
        THE_RUN,
        "merchantId",
        "merchant-1",
        "ownerId",
        "customer-openapi",
        "type",
        "card",
        "alias",
        alias,
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "4242",
            "expMonth",
            12,
            "expYear",
            2030,
            "funding",
            "credit"));
  }

  private static Map<String, Object> advance() {
    return Map.of("testRunId", THE_RUN, "advanceBy", "PT1H");
  }
}
