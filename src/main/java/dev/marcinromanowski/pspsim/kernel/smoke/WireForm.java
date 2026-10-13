package dev.marcinromanowski.pspsim.kernel.smoke;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WireForm {

  private WireForm() {}

  public static String encoded(Map<String, String> fields) {
    List<String> pairs = new ArrayList<>();
    fields.forEach(
        (name, value) ->
            pairs.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8)));
    return String.join("&", pairs);
  }
}
