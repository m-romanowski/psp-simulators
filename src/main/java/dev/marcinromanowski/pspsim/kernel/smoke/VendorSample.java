package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class VendorSample {

  private static final CheckJson JSON = new CheckJson();

  private final Provenance provenance;
  private final @Nullable String status;
  private final Map<String, String> headers;
  private final @Nullable String body;
  private final Map<String, String> rules;

  VendorSample(
      Provenance provenance,
      @Nullable String status,
      Map<String, String> headers,
      @Nullable String body,
      Map<String, String> rules) {
    this.provenance = provenance;
    this.status = status;
    this.headers = Map.copyOf(headers);
    this.body = body;
    this.rules = Map.copyOf(rules);
  }

  public Provenance provenance() {
    return provenance;
  }

  public Published status() {
    return new Published(recorded(status, "HTTP status"), provenance);
  }

  public Published header(String name) {
    String carried = headers.get(name.toLowerCase(Locale.ROOT));
    if (carried == null) {
      throw new MalformedVendorSampleException(
          provenance.sample() + " carries no header " + name + " for a check to read");
    }
    return new Published(carried, provenance);
  }

  public Published body() {
    return new Published(recorded(body, "body"), provenance);
  }

  public Published json(String pointer) {
    String text = recorded(body, "body");
    try {
      return new Published(JSON.at(text, pointer), provenance);
    } catch (IllegalStateException notJson) {
      throw new MalformedVendorSampleException(
          provenance.sample() + " has a body that is not JSON for a check to read " + pointer);
    }
  }

  public Published rule(String name) {
    return new Published(recorded(rules.get(name), "rule value " + name), provenance);
  }

  private String recorded(@Nullable String part, String what) {
    if (part == null) {
      throw new MalformedVendorSampleException(
          provenance.sample() + " records no " + what + " for a check to read");
    }
    return part;
  }
}
