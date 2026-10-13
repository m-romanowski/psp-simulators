package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

public record GrowApiCall(
    String method,
    InboundRoute route,
    Map<String, List<String>> headers,
    Map<String, List<String>> form) {

  public GrowApiCall {
    headers = deeplyImmutable(headers);
    form = deeplyImmutable(form);
  }

  public GrowApiCall(
      String method,
      String path,
      Map<String, List<String>> headers,
      Map<String, List<String>> form) {
    this(method, InboundRoute.notAliased(path), headers, form);
  }

  public String path() {
    return route.calledUrl();
  }

  private static SequencedMap<String, List<String>> deeplyImmutable(
      Map<String, List<String>> values) {
    LinkedHashMap<String, List<String>> inTheOrderTheCallerSentThem = new LinkedHashMap<>();
    values.forEach((name, sent) -> inTheOrderTheCallerSentThem.put(name, List.copyOf(sent)));
    return Collections.unmodifiableSequencedMap(inTheOrderTheCallerSentThem);
  }
}
