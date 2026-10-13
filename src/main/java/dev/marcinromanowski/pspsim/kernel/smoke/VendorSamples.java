package dev.marcinromanowski.pspsim.kernel.smoke;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

public final class VendorSamples {

  static final String ROOT = "vendor-samples/";

  static final String MANIFEST = "samples.json";

  static final String SIMULATOR = "simulator";

  private static final int LOWEST_STATUS = 100;

  private static final int HIGHEST_STATUS = 599;

  private static final String JSON_FILE = ".json";

  private static final String EVIDENCE = "evidence";
  private static final String PROBED = "probed";
  private static final String EXCERPT = "excerpt";
  private static final String NOTE = "note";
  private static final String SOURCE = "source";
  private static final String REFERENCE = "reference";
  private static final String STATUS = "status";
  private static final String HEADERS = "headers";
  private static final String BODY = "body";
  private static final String RULES = "rules";

  private static final JsonMapper STRICT =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private final String owner;
  private final Map<String, VendorSample> samples;

  private VendorSamples(String owner, Map<String, VendorSample> samples) {
    this.owner = owner;
    this.samples = Map.copyOf(samples);
  }

  public static VendorSamples of(String owner) {
    return read(owner, VendorSamples::onTheClasspath);
  }

  static VendorSamples read(String owner, Function<String, @Nullable String> resources) {
    String manifest = ROOT + owner + "/" + MANIFEST;
    Object declared = parsed(manifest, resource(resources, manifest));
    if (!(declared instanceof Map<?, ?> entries) || entries.isEmpty()) {
      throw new MalformedVendorSampleException(
          manifest + " must be a JSON object naming at least one sample");
    }
    Map<String, VendorSample> read = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : entries.entrySet()) {
      String name = owner + "/" + entry.getKey();
      read.put(String.valueOf(entry.getKey()), sampleOf(resources, owner, name, entry.getValue()));
    }
    return new VendorSamples(owner, read);
  }

  public VendorSample sample(String name) {
    VendorSample found = samples.get(name);
    if (found == null) {
      throw new VendorSampleNotFoundException(
          ROOT + owner + "/" + MANIFEST + " declares no sample " + name);
    }
    return found;
  }

  private static VendorSample sampleOf(
      Function<String, @Nullable String> resources,
      String owner,
      String name,
      @Nullable Object declared) {
    if (!(declared instanceof Map<?, ?> fields)) {
      throw new MalformedVendorSampleException(name + " must be declared as a JSON object");
    }
    EvidenceClass kind = EvidenceClass.spelled(name, text(name, fields, EVIDENCE));
    if ((kind == EvidenceClass.SIMULATOR_CONTRACT) != SIMULATOR.equals(owner)) {
      throw new MalformedVendorSampleException(
          name
              + " names evidence class "
              + kind.spelling()
              + ", and only the "
              + SIMULATOR
              + "/ manifest carries the "
              + EvidenceClass.SIMULATOR_CONTRACT.spelling()
              + ", which is the only class it carries");
    }
    Set<String> carried = fieldsOf(kind);
    for (Object field : fields.keySet()) {
      if (!carried.contains(String.valueOf(field))) {
        throw new MalformedVendorSampleException(
            name + " declares " + field + ", which a " + kind.spelling() + " does not carry");
      }
    }
    Provenance provenance =
        new Provenance(
            name,
            evidenceOf(name, kind, fields),
            text(name, fields, SOURCE),
            text(name, fields, REFERENCE),
            optionalText(name, fields, NOTE));
    Map<String, String> rules = textsOf(name, RULES, fields.get(RULES));
    if (kind == EvidenceClass.PUBLISHED_RULE && rules.isEmpty()) {
      throw new MalformedVendorSampleException(
          name + " is a published rule and names no value the rule publishes");
    }
    return new VendorSample(
        provenance,
        statusOf(name, fields.get(STATUS)),
        headersOf(name, fields.get(HEADERS)),
        bodyOf(resources, owner, name, optionalText(name, fields, BODY)),
        rules);
  }

  private static @Nullable String statusOf(String name, @Nullable Object declared) {
    if (declared == null) {
      return null;
    }
    if (!(declared instanceof Integer status)
        || status < LOWEST_STATUS
        || status > HIGHEST_STATUS) {
      throw new MalformedVendorSampleException(
          name
              + " must give status an HTTP status, an integer from "
              + LOWEST_STATUS
              + " to "
              + HIGHEST_STATUS);
    }
    return String.valueOf(status);
  }

  private static Map<String, String> headersOf(String name, @Nullable Object declared) {
    Map<String, String> headers = new LinkedHashMap<>();
    for (Map.Entry<String, String> header : textsOf(name, HEADERS, declared).entrySet()) {
      String normalised = header.getKey().toLowerCase(Locale.ROOT);
      if (headers.putIfAbsent(normalised, header.getValue()) != null) {
        throw new MalformedVendorSampleException(
            name + " declares the header " + header.getKey() + " twice, in two cases");
      }
    }
    return headers;
  }

  private static @Nullable String bodyOf(
      Function<String, @Nullable String> resources,
      String owner,
      String name,
      @Nullable String file) {
    if (file == null) {
      return null;
    }
    if (file.contains("/") || file.contains("\\") || file.startsWith(".")) {
      throw new MalformedVendorSampleException(
          name + " names the body " + file + ", which is not a file of its own directory");
    }
    String path = ROOT + owner + "/" + file;
    String body = resource(resources, path);
    if (file.endsWith(JSON_FILE)) {
      parsed(path, body);
    }
    return body;
  }

  private static Set<String> fieldsOf(EvidenceClass kind) {
    return switch (kind) {
      case PUBLISHED_RULE -> Set.of(EVIDENCE, NOTE, SOURCE, REFERENCE, RULES);
      case LIVE_PROBE ->
          Set.of(EVIDENCE, PROBED, EXCERPT, NOTE, SOURCE, REFERENCE, STATUS, HEADERS, BODY);
      case PUBLISHED_EXAMPLE, SIMULATOR_CONTRACT ->
          Set.of(EVIDENCE, EXCERPT, NOTE, SOURCE, REFERENCE, STATUS, HEADERS, BODY);
    };
  }

  private static Evidence evidenceOf(String name, EvidenceClass kind, Map<?, ?> fields) {
    Evidence evidence =
        kind == EvidenceClass.LIVE_PROBE
            ? Evidence.probedOn(dayOf(name, text(name, fields, PROBED)))
            : Evidence.of(kind);
    Object excerpt = fields.get(EXCERPT);
    if (excerpt != null && !(excerpt instanceof Boolean)) {
      throw new MalformedVendorSampleException(name + " must give excerpt true or false");
    }
    return Boolean.TRUE.equals(excerpt) ? evidence.excerpted() : evidence;
  }

  private static LocalDate dayOf(String name, String probed) {
    try {
      return LocalDate.parse(probed);
    } catch (DateTimeParseException notADay) {
      throw new MalformedVendorSampleException(
          name + " gives probed " + probed + ", which is not a day written as 2026-09-10");
    }
  }

  private static Map<String, String> textsOf(String name, String field, @Nullable Object declared) {
    Object named = declared == null ? Map.of() : declared;
    if (!(named instanceof Map<?, ?> carried)) {
      throw new MalformedVendorSampleException(
          name + " must declare its " + field + " as an object");
    }
    Map<String, String> texts = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : carried.entrySet()) {
      if (!(entry.getValue() instanceof String value)) {
        throw new MalformedVendorSampleException(
            name + " must give " + entry.getKey() + " in its " + field + " a text value");
      }
      if (value.isBlank()) {
        throw new MalformedVendorSampleException(
            name + " gives " + entry.getKey() + " in its " + field + " no text");
      }
      texts.put(String.valueOf(entry.getKey()), value);
    }
    return texts;
  }

  private static String text(String name, Map<?, ?> fields, String field) {
    String value = optionalText(name, fields, field);
    if (value == null) {
      throw new MalformedVendorSampleException(name + " declares no " + field);
    }
    return value;
  }

  private static @Nullable String optionalText(String name, Map<?, ?> fields, String field) {
    Object value = fields.get(field);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String) && !(value instanceof Number)) {
      throw new MalformedVendorSampleException(name + " must give " + field + " a scalar value");
    }
    return String.valueOf(value);
  }

  private static @Nullable Object parsed(String file, String text) {
    try {
      return STRICT.readValue(text, Object.class);
    } catch (JacksonException unreadable) {
      throw new MalformedVendorSampleException(
          file + " is not JSON that names each member once: " + unreadable.getOriginalMessage());
    }
  }

  private static String resource(Function<String, @Nullable String> resources, String path) {
    String text = resources.apply(path);
    if (text == null) {
      throw new VendorSampleNotFoundException(path + " does not exist");
    }
    return text;
  }

  private static @Nullable String onTheClasspath(String path) {
    try (InputStream stream = VendorSamples.class.getClassLoader().getResourceAsStream(path)) {
      return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
