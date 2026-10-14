package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KeyedAnswer(int httpStatus, String contentType, Map<String, List<String>> fields) {

  public KeyedAnswer {
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }
}
