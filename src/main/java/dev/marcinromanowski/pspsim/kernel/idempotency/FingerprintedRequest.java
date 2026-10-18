package dev.marcinromanowski.pspsim.kernel.idempotency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record FingerprintedRequest(String method, String path, Map<String, List<String>> form) {

  private static final char BETWEEN_NAME_AND_VALUE = '=';

  public FingerprintedRequest {
    form = Map.copyOf(form);
  }

  public List<String> comparedParameters() {
    List<String> parameters = new ArrayList<>();
    form.forEach(
        (name, values) -> {
          for (String value : values) {
            parameters.add(name + BETWEEN_NAME_AND_VALUE + value);
          }
        });
    Collections.sort(parameters);
    return List.copyOf(parameters);
  }

  public boolean isTheSameRequestAs(FingerprintedRequest other) {
    return method.equals(other.method)
        && path.equals(other.path)
        && comparedParameters().equals(other.comparedParameters());
  }
}
