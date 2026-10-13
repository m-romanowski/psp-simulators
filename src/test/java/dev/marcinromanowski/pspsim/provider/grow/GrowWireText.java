package dev.marcinromanowski.pspsim.provider.grow;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class GrowWireText {

  private GrowWireText() {}

  private static final int ESCAPE_LENGTH = 6;

  private static final int HEXADECIMAL = 16;

  private static final ObjectMapper JSON = new ObjectMapper();

  public static String readable(String wire) {
    StringBuilder out = new StringBuilder();
    int at = 0;
    while (at < wire.length()) {
      char current = wire.charAt(at);
      if (current != '\\' || at + 1 >= wire.length()) {
        out.append(current);
        at++;
        continue;
      }
      if (wire.charAt(at + 1) == 'u' && at + ESCAPE_LENGTH <= wire.length()) {
        out.append(
            (char) Integer.parseInt(wire.substring(at + 2, at + ESCAPE_LENGTH), HEXADECIMAL));
        at += ESCAPE_LENGTH;
        continue;
      }
      out.append(current).append(wire.charAt(at + 1));
      at += 2;
    }
    return out.toString();
  }

  public static Map<String, Object> asJson(byte[] body) {
    return JSON.readValue(body, new TypeReference<Map<String, Object>>() {});
  }
}
