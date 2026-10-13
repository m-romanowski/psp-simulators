package dev.marcinromanowski.pspsim.provider.grow;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class GrowJsonWriter {

  private GrowJsonWriter() {}

  private static final char HIGHEST_UNESCAPED = 0x7e;

  static byte[] toBytes(GrowJson value, GrowWireForm wireForm) {
    StringBuilder out = new StringBuilder();
    write(value, out, wireForm);
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void write(GrowJson value, StringBuilder out, GrowWireForm wireForm) {
    switch (value) {
      case GrowJson.JsonText text -> writeString(text.value(), out, wireForm);
      case GrowJson.JsonNumber number -> out.append(number.value());
      case GrowJson.JsonObject object -> writeObject(object, out, wireForm);
      case GrowJson.JsonArray array -> writeArray(array, out, wireForm);
    }
  }

  private static void writeArray(
      GrowJson.JsonArray array, StringBuilder out, GrowWireForm wireForm) {
    out.append('[');
    boolean first = true;
    for (GrowJson element : array.elements()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      write(element, out, wireForm);
    }
    out.append(']');
  }

  private static void writeObject(
      GrowJson.JsonObject object, StringBuilder out, GrowWireForm wireForm) {
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, GrowJson> member : object.members().entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeString(member.getKey(), out, wireForm);
      out.append(':');
      write(member.getValue(), out, wireForm);
    }
    out.append('}');
  }

  private static void writeString(String value, StringBuilder out, GrowWireForm wireForm) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20 || (c > HIGHEST_UNESCAPED && wireForm.escapesNonAscii())) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
