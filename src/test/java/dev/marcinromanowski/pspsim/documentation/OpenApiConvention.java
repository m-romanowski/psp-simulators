package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class OpenApiConvention {

  public static final String PUBLISHED_UNDER = "src/main/resources/static/openapi";

  public static final String INDEX = "index.json";

  public static final String PAGE = "index.html";

  public static final String CONTROL_DOCUMENT = "control.json";

  public static final String OPENAPI = "3.1.0";

  static final Set<String> A_VERB_THAT_CARRIES_A_BODY = Set.of("post", "put", "patch");

  private static final String ROUTE = "route=";

  private static final String REFUSAL = "refusal=";

  private static final String SWAGGER_UI = "swaggerUi=";

  private static final String PRODUCT = "product=";

  private static final Set<String> KEYS_THAT_WOULD_CARRY_A_VALUE =
      Set.of("example", "examples", "const", "default");

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private OpenApiConvention() {}

  public enum Shape {
    OBJECT,
    ARRAY
  }

  public record Answer(Shape shape, String recordClass) {

    public static Answer parse(String specification) {
      List<String> fields = List.of(specification.split("\\|", -1));
      if (fields.size() != 2) {
        throw new IllegalArgumentException("an answer is shape|recordClass, not " + specification);
      }
      return new Answer(Shape.valueOf(fields.get(0)), fields.get(1));
    }
  }

  public record Finding(String message) {

    @Override
    public String toString() {
      return message;
    }
  }

  public record Published(Map<String, String> files, List<Finding> findings, int routesPublished) {}

  public static Published build(
      Path classesRoot,
      Map<String, Answer> answers,
      List<String> refusalRecords,
      String swaggerUiVersion,
      String productVersion) {
    List<Finding> findings = new ArrayList<>();
    List<RoutesInTheBuild.Route> routes = RoutesInTheBuild.under(classesRoot);
    Map<String, List<RoutesInTheBuild.Route>> byFamily = new TreeMap<>();
    for (RoutesInTheBuild.Route route : routes) {
      byFamily.computeIfAbsent(route.family(), family -> new ArrayList<>()).add(route);
    }
    Map<String, String> files = new LinkedHashMap<>();
    List<ObjectNode> listed = new ArrayList<>();
    int published = 0;

    List<RoutesInTheBuild.Route> control =
        byFamily.getOrDefault(RoutesInTheBuild.CONTROL, List.of());
    if (control.isEmpty()) {
      findings.add(
          new Finding(
              "no route of this build sits in the control package, and the browsable API view"
                  + " exists to publish the control API, so either the controller moved or the"
                  + " reader of the compiled classes has stopped seeing it"));
    }
    files.put(
        CONTROL_DOCUMENT,
        written(
            controlDocument(
                classesRoot, control, answers, refusalRecords, productVersion, findings)));
    listed.add(entry("Simulator control API", CONTROL_DOCUMENT));
    published += control.size();

    for (Map.Entry<String, List<RoutesInTheBuild.Route>> family : byFamily.entrySet()) {
      if (!family.getKey().startsWith(RoutesInTheBuild.PROVIDER)) {
        continue;
      }
      String provider = family.getKey().substring(RoutesInTheBuild.PROVIDER.length());
      String file = provider + ".json";
      files.put(
          file,
          written(providerDocument(classesRoot, provider, family.getValue(), productVersion)));
      listed.add(entry("Provider " + provider, file));
      published += family.getValue().size();
    }

    for (String signature : new TreeSet<>(answers.keySet())) {
      if (control.stream().noneMatch(route -> route.signature().equals(signature))) {
        findings.add(
            new Finding(
                "`openApiControlAnswers` binds "
                    + signature
                    + " to "
                    + CompiledRecords.readable(answers.get(signature).recordClass())
                    + " and no control route of this build answers on it, so the binding publishes"
                    + " a route nothing serves"));
      }
    }

    ArrayNode index = NODES.arrayNode();
    listed.forEach(index::add);
    files.put(INDEX, written(index));
    files.put(PAGE, page(swaggerUiVersion));

    for (Map.Entry<String, String> file : files.entrySet()) {
      if (file.getKey().endsWith(".json")) {
        forbidValues(file.getKey(), read(file.getValue()), "", findings);
      }
    }
    return new Published(Map.copyOf(files), List.copyOf(findings), published);
  }

  private static ObjectNode entry(String name, String url) {
    ObjectNode listed = NODES.objectNode();
    listed.put("name", name);
    listed.put("url", url);
    return listed;
  }

  private static ObjectNode controlDocument(
      Path classesRoot,
      List<RoutesInTheBuild.Route> routes,
      Map<String, Answer> answers,
      List<String> refusalRecords,
      String productVersion,
      List<Finding> findings) {
    JsonSchemaOfARecord schemas =
        new JsonSchemaOfARecord(new CompiledRecords(classesRoot), classesRoot);
    ObjectNode paths = NODES.objectNode();
    for (RoutesInTheBuild.Route route : routes) {
      if (route.bodyArrivesUnparsed()) {
        findings.add(
            new Finding(
                route.signature()
                    + " takes the servlet request itself rather than a body Spring binds to a"
                    + " record, and this document publishes a control request schema by reading"
                    + " that record. A control route that parses its own body publishes no request"
                    + " schema at all, which is how the published schema drifted from the code"
                    + " before. Bind the body to a record, or move the route to a provider"
                    + " contract, whose bodies are declared untyped and cited to a document."));
      }
      Answer answer = answers.get(route.signature());
      if (answer == null) {
        findings.add(
            new Finding(
                route.signature()
                    + " is a control route of this build and `openApiControlAnswers` binds it to no"
                    + " record, so the browsable API view would show a route whose answer it does"
                    + " not state. Name the record its success body is built from and whether it"
                    + " answers that record or an array of it."));
        continue;
      }
      ObjectNode operation = operationOn(paths, route);
      requestBodyOf(schemas, route).ifPresent(body -> operation.set("requestBody", body));
      operation.set("responses", controlResponses(schemas, answer, refusalRecords));
    }
    ObjectNode document = document("PSP simulator control API", productVersion, controlPreamble());
    document.set("paths", paths);
    ObjectNode components = NODES.objectNode();
    ObjectNode defined = NODES.objectNode();
    for (Map.Entry<String, ObjectNode> schema :
        new TreeMap<>(schemas.schemasDefined()).entrySet()) {
      defined.set(schema.getKey(), schema.getValue());
    }
    components.set("schemas", defined);
    document.set("components", components);
    return document;
  }

  private static ObjectNode controlResponses(
      JsonSchemaOfARecord schemas, Answer answer, List<String> refusalRecords) {
    ObjectNode responses = NODES.objectNode();
    ObjectNode success = NODES.objectNode();
    success.put(
        "description",
        "the body this route answers on success, generated from "
            + CompiledRecords.readable(answer.recordClass())
            + " as this build compiled it");
    ObjectNode body = schemas.referenceTo(answer.recordClass());
    if (answer.shape() == Shape.ARRAY) {
      ObjectNode array = NODES.objectNode();
      array.put("type", "array");
      array.set("items", body);
      body = array;
    }
    success.set("content", json(body));
    responses.set("2XX", success);
    ObjectNode refused = NODES.objectNode();
    refused.put(
        "description",
        "every refusal of this API carries a message, and a refusal naming the properties it"
            + " refused carries those too");
    ArrayNode either = NODES.arrayNode();
    for (String refusal : refusalRecords) {
      either.add(schemas.referenceTo(refusal));
    }
    ObjectNode union = NODES.objectNode();
    union.set("anyOf", either);
    refused.set("content", json(union));
    responses.set("default", refused);
    return responses;
  }

  private static ObjectNode providerDocument(
      Path classesRoot,
      String provider,
      List<RoutesInTheBuild.Route> routes,
      String productVersion) {
    JsonSchemaOfARecord schemas =
        new JsonSchemaOfARecord(new CompiledRecords(classesRoot), classesRoot);
    String cited = "doc/" + provider + ".md";
    ObjectNode paths = NODES.objectNode();
    for (RoutesInTheBuild.Route route : routes) {
      ObjectNode operation = operationOn(paths, route);
      operation.put(
          "description",
          "provider "
              + provider
              + " answers this route in its own wire format, which no record of this build"
              + " describes, so no schema is published for it here. Its shape is specified in "
              + cited
              + ".");
      ObjectNode responses = NODES.objectNode();
      ObjectNode answered = NODES.objectNode();
      answered.put(
          "description", "the body provider " + provider + " answers, specified in " + cited);
      answered.set("content", anything());
      responses.set("default", answered);
      operation.set("responses", responses);
      Optional<ObjectNode> typed = requestBodyOf(schemas, route);
      if (typed.isPresent()) {
        operation.set("requestBody", typed.get());
      } else if (route.bodyArrivesUnparsed() && carriesABody(route)) {
        ObjectNode sent = NODES.objectNode();
        sent.put(
            "description",
            "provider "
                + provider
                + " reads this body in its own wire format, specified in "
                + cited);
        sent.set("content", anything());
        sent.put("required", true);
        operation.set("requestBody", sent);
      }
    }
    ObjectNode document =
        document(
            "PSP simulator provider " + provider,
            productVersion,
            providerPreamble(provider, cited));
    document.set("paths", paths);
    if (!schemas.schemasDefined().isEmpty()) {
      ObjectNode components = NODES.objectNode();
      ObjectNode defined = NODES.objectNode();
      for (Map.Entry<String, ObjectNode> schema :
          new TreeMap<>(schemas.schemasDefined()).entrySet()) {
        defined.set(schema.getKey(), schema.getValue());
      }
      components.set("schemas", defined);
      document.set("components", components);
    }
    return document;
  }

  private static ObjectNode anything() {
    ObjectNode content = NODES.objectNode();
    content.set("*/*", withSchema(NODES.objectNode()));
    return content;
  }

  private static boolean carriesABody(RoutesInTheBuild.Route route) {
    return A_VERB_THAT_CARRIES_A_BODY.contains(route.httpMethod().toLowerCase(Locale.ROOT));
  }

  private static ObjectNode json(ObjectNode schema) {
    ObjectNode content = NODES.objectNode();
    content.set("application/json", withSchema(schema));
    return content;
  }

  private static ObjectNode withSchema(ObjectNode schema) {
    ObjectNode media = NODES.objectNode();
    media.set("schema", schema);
    return media;
  }

  private static Optional<ObjectNode> requestBodyOf(
      JsonSchemaOfARecord schemas, RoutesInTheBuild.Route route) {
    Optional<String> named = route.requestBodyClass();
    if (named.isEmpty()) {
      return Optional.empty();
    }
    ObjectNode body = NODES.objectNode();
    body.put(
        "description",
        "generated from "
            + CompiledRecords.readable(named.get())
            + " as this build compiled it; a component that may be left out is typed to allow"
            + " null and is not required");
    body.set("content", json(schemas.referenceTo(named.get())));
    body.put("required", route.requestBodyRequired());
    return Optional.of(body);
  }

  private static ObjectNode operationOn(ObjectNode paths, RoutesInTheBuild.Route route) {
    ObjectNode path = (ObjectNode) paths.get(route.path());
    if (path == null) {
      path = NODES.objectNode();
      paths.set(route.path(), path);
    }
    ObjectNode operation = (ObjectNode) path.get(route.httpMethod());
    if (operation == null) {
      operation = NODES.objectNode();
      path.set(route.httpMethod(), operation);
      operation.put("operationId", route.operationId());
      ArrayNode parameters = NODES.arrayNode();
      for (RoutesInTheBuild.Parameter parameter : route.parameters()) {
        ObjectNode declared = NODES.objectNode();
        declared.put("name", parameter.name());
        declared.put("in", parameter.in());
        declared.put("required", parameter.required());
        ObjectNode type = NODES.objectNode();
        type.put("type", "string");
        declared.set("schema", type);
        parameters.add(declared);
      }
      if (!parameters.isEmpty()) {
        operation.set("parameters", parameters);
      }
    }
    return operation;
  }

  private static ObjectNode document(String title, String productVersion, String description) {
    ObjectNode document = NODES.objectNode();
    document.put("openapi", OPENAPI);
    ObjectNode info = NODES.objectNode();
    info.put("title", title);
    info.put("version", productVersion);
    if (!description.isEmpty()) {
      info.put("description", description);
    }
    document.set("info", info);
    ArrayNode servers = NODES.arrayNode();
    ObjectNode here = NODES.objectNode();
    here.put("url", "/");
    servers.add(here);
    document.set("servers", servers);
    return document;
  }

  private static String controlPreamble() {
    return "Every schema in this document was generated from the record that serves it, read out of"
        + " the compiled class files of this build, and the build fails when the two disagree. A"
        + " component that can arrive null is typed to allow null and is not required; no other"
        + " component may be absent. Nothing here carries an example, because an example would be"
        + " a value and this document states shapes only: a field that holds a credential is named"
        + " and never shown.";
  }

  private static String providerPreamble(String provider, String documentPath) {
    return "Provider "
        + provider
        + " answers this simulator's imitation of its vendor's own wire format, assembled by hand"
        + " and described by no record of this build, so every body below is published untyped"
        + " rather than published as a shape nothing could check. "
        + documentPath
        + " describes the shapes it really answers.";
  }

  static void forbidValuesIn(String file, JsonNode document, List<Finding> findings) {
    forbidValues(file, document, "", findings);
  }

  private static void forbidValues(String file, JsonNode node, String at, List<Finding> findings) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> property : node.properties()) {
        String path = at.isEmpty() ? property.getKey() : at + "." + property.getKey();
        if (KEYS_THAT_WOULD_CARRY_A_VALUE.contains(property.getKey())
            && !at.endsWith("responses")) {
          findings.add(
              new Finding(
                  file
                      + " publishes `"
                      + path
                      + "`, and this API view states shapes and never values: an example carries"
                      + " whatever it was taken from, and a control request carries provider"
                      + " credentials and webhook secrets. Name the field and publish no value"
                      + " for it."));
        }
        forbidValues(file, property.getValue(), path, findings);
      }
      return;
    }
    if (node.isArray()) {
      for (int element = 0; element < node.size(); element++) {
        forbidValues(file, node.get(element), at + "[" + element + "]", findings);
      }
    }
  }

  private static String page(String swaggerUiVersion) {
    String assets = "/webjars/swagger-ui/" + swaggerUiVersion + "/";
    return "<!DOCTYPE html>\n"
        + "<html lang=\"en\">\n"
        + "  <head>\n"
        + "    <meta charset=\"utf-8\" />\n"
        + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n"
        + "    <title>PSP simulator · API</title>\n"
        + "    <link rel=\"stylesheet\" href=\""
        + assets
        + "swagger-ui.css\" />\n"
        + "    <link rel=\"stylesheet\" href=\"/ui.css\" />\n"
        + "  </head>\n"
        + "  <body>\n"
        + "    <header class=\"bar\">\n"
        + "      <h1>PSP simulator</h1>\n"
        + "      <nav>\n"
        + "        <a href=\"/ui\">This test run</a>\n"
        + "        <a class=\"here\" href=\"/openapi/index.html\">API</a>\n"
        + "      </nav>\n"
        + "    </header>\n"
        + "    <main>\n"
        + "      <div id=\"the-api\"></div>\n"
        + "    </main>\n"
        + "    <script src=\""
        + assets
        + "swagger-ui-bundle.js\"></script>\n"
        + "    <script src=\""
        + assets
        + "swagger-ui-standalone-preset.js\"></script>\n"
        + "    <script>\n"
        + "      fetch('index.json')\n"
        + "        .then(function (answered) { return answered.json(); })\n"
        + "        .then(function (documents) {\n"
        + "          window.ui = SwaggerUIBundle({\n"
        + "            urls: documents,\n"
        + "            dom_id: '#the-api',\n"
        + "            deepLinking: true,\n"
        + "            tryItOutEnabled: true,\n"
        + "            validatorUrl: null,\n"
        + "            presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],\n"
        + "            layout: 'StandaloneLayout'\n"
        + "          });\n"
        + "        });\n"
        + "    </script>\n"
        + "  </body>\n"
        + "</html>\n";
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private static String written(JsonNode document) {
    DefaultPrettyPrinter printer =
        new DefaultPrettyPrinter()
            .withSeparators(
                Separators.createDefaultInstance()
                    .withObjectNameValueSpacing(Separators.Spacing.AFTER))
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));
    return JSON.writer().with(printer).writeValueAsString(document).replace("\r\n", "\n") + "\n";
  }

  private static JsonNode read(String text) {
    return JSON.readTree(text);
  }

  private static String readString(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  public static void main(String[] args) {
    Path root = Path.of(args[0]);
    Path classesRoot = Path.of(args[1]);
    String mode = args[2];
    Map<String, Answer> answers = new LinkedHashMap<>();
    List<String> refusals = new ArrayList<>();
    String swaggerUiVersion = "";
    String productVersion = "";
    for (int at = 3; at < args.length; at++) {
      String argument = args[at];
      if (argument.startsWith(ROUTE)) {
        String specification = argument.substring(ROUTE.length());
        int split = specification.indexOf('=');
        answers.put(
            specification.substring(0, split), Answer.parse(specification.substring(split + 1)));
      } else if (argument.startsWith(REFUSAL)) {
        refusals.add(argument.substring(REFUSAL.length()));
      } else if (argument.startsWith(SWAGGER_UI)) {
        swaggerUiVersion = argument.substring(SWAGGER_UI.length());
      } else if (argument.startsWith(PRODUCT)) {
        productVersion = argument.substring(PRODUCT.length());
      } else {
        throw new IllegalArgumentException("unreadable argument " + argument);
      }
    }
    Published published = build(classesRoot, answers, refusals, swaggerUiVersion, productVersion);
    Path under = root.resolve(PUBLISHED_UNDER);
    if ("write".equals(mode)) {
      write(under, published);
      System.out.println(
          "writeOpenApi: "
              + published.files().size()
              + " files under "
              + PUBLISHED_UNDER
              + ", "
              + published.routesPublished()
              + " routes.");
      if (!published.findings().isEmpty()) {
        refuse(published.findings());
      }
      return;
    }
    List<Finding> findings = new ArrayList<>(published.findings());
    findings.addAll(drift(under, published));
    if (!findings.isEmpty()) {
      refuse(findings);
    }
    System.out.println(
        "verifyOpenApi: "
            + published.routesPublished()
            + " routes and "
            + published.files().size()
            + " published files match the compiled records that serve them.");
  }

  private static void refuse(List<Finding> findings) {
    StringBuilder failure = new StringBuilder();
    failure.append(
        "The browsable API view under "
            + PUBLISHED_UNDER
            + " is generated from the compiled classes of this build and committed, so a change to"
            + " a payload record cannot leave it stale. It is stale:\n");
    Set<String> said = new LinkedHashSet<>();
    for (Finding finding : findings) {
      if (said.add(finding.message())) {
        failure.append("  - ").append(finding).append('\n');
      }
    }
    failure.append("Run `./gradlew writeOpenApi` and commit what it writes.\n");
    System.out.println(failure);
    System.exit(1);
  }

  private static List<Finding> drift(Path under, Published published) {
    List<Finding> findings = new ArrayList<>();
    Set<String> expected = new TreeSet<>(published.files().keySet());
    for (String name : expected) {
      Path file = under.resolve(name);
      if (!Files.isRegularFile(file)) {
        findings.add(
            new Finding(
                PUBLISHED_UNDER + "/" + name + " is generated by this build and is not committed"));
        continue;
      }
      String committed = readString(file).replace("\r\n", "\n");
      String generated = published.files().get(name);
      if (!committed.equals(generated)) {
        findings.add(
            new Finding(
                PUBLISHED_UNDER
                    + "/"
                    + name
                    + " differs from what this build generates, "
                    + firstDifference(name, committed, generated)));
      }
    }
    Set<String> found = new TreeSet<>();
    try (var listed = Files.list(under)) {
      listed.filter(Files::isRegularFile).forEach(file -> found.add(file.getFileName().toString()));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    found.removeAll(expected);
    for (String stale : found) {
      findings.add(
          new Finding(
              PUBLISHED_UNDER
                  + "/"
                  + stale
                  + " is committed under the published API view and this build generates no such"
                  + " file, so a reader browses a document nothing in this build stands behind"));
    }
    return List.copyOf(findings);
  }

  static String firstDifference(String name, String committed, String generated) {
    if (name.endsWith(".json")) {
      Optional<String> structural = differenceBetween(read(committed), read(generated), "");
      if (structural.isPresent()) {
        return structural.get();
      }
    }
    List<String> was = List.of(committed.split("\n", -1));
    List<String> is = List.of(generated.split("\n", -1));
    for (int line = 0; line < Math.max(was.size(), is.size()); line++) {
      String left = line < was.size() ? was.get(line) : "(end of file)";
      String right = line < is.size() ? is.get(line) : "(end of file)";
      if (!left.equals(right)) {
        return "first at line "
            + (line + 1)
            + ": committed `"
            + left.strip()
            + "`, generated `"
            + right.strip()
            + "`";
      }
    }
    return "in a way no comparison of its text finds";
  }

  private static Optional<String> differenceBetween(
      JsonNode committed, JsonNode generated, String at) {
    String where = at.isEmpty() ? "the whole document" : "`" + at + "`";
    if (committed.isObject() && generated.isObject()) {
      Set<String> was = new LinkedHashSet<>();
      committed.propertyNames().forEach(was::add);
      Set<String> is = new LinkedHashSet<>();
      generated.propertyNames().forEach(is::add);
      Set<String> gone = new TreeSet<>(was);
      gone.removeAll(is);
      Set<String> added = new TreeSet<>(is);
      added.removeAll(was);
      if (!gone.isEmpty() || !added.isEmpty()) {
        return Optional.of(
            "at "
                + where
                + ": the code now serves "
                + (added.isEmpty() ? "nothing new" : added.toString())
                + " and no longer serves "
                + (gone.isEmpty() ? "nothing the view published" : gone.toString()));
      }
      for (String key : was) {
        Optional<String> deeper =
            differenceBetween(
                committed.get(key), generated.get(key), at.isEmpty() ? key : at + "." + key);
        if (deeper.isPresent()) {
          return deeper;
        }
      }
      return Optional.empty();
    }
    if (committed.isArray() && generated.isArray()) {
      if (committed.size() != generated.size()) {
        return Optional.of(
            "at "
                + where
                + ": the view lists "
                + committed.size()
                + " and the code serves "
                + generated.size());
      }
      for (int element = 0; element < committed.size(); element++) {
        Optional<String> deeper =
            differenceBetween(
                committed.get(element), generated.get(element), at + "[" + element + "]");
        if (deeper.isPresent()) {
          return deeper;
        }
      }
      return Optional.empty();
    }
    if (committed.equals(generated)) {
      return Optional.empty();
    }
    return Optional.of(
        "at " + where + ": the view publishes " + committed + " and the code serves " + generated);
  }

  private static void write(Path under, Published published) {
    try {
      Files.createDirectories(under);
      for (Map.Entry<String, String> file : published.files().entrySet()) {
        Files.writeString(under.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
      }
      try (var listed = Files.list(under)) {
        List<Path> stale =
            listed
                .filter(Files::isRegularFile)
                .filter(file -> !published.files().containsKey(file.getFileName().toString()))
                .toList();
        for (Path file : stale) {
          Files.delete(file);
        }
      }
    } catch (IOException unwritable) {
      throw new UncheckedIOException(unwritable);
    }
  }
}
