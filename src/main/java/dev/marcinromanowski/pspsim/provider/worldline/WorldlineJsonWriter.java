package dev.marcinromanowski.pspsim.provider.worldline;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class WorldlineJsonWriter {

  private static final char HIGHEST_UNESCAPED = 0x7e;

  private WorldlineJsonWriter() {}

  static byte[] toBytes(WorldlineJson value) {
    return toText(value).getBytes(StandardCharsets.UTF_8);
  }

  static String toText(WorldlineJson value) {
    StringBuilder out = new StringBuilder();
    write(value, out);
    return out.toString();
  }

  private static void write(WorldlineJson value, StringBuilder out) {
    switch (value) {
      case WorldlineJson.JsonText text -> writeString(text.value(), out);
      case WorldlineJson.JsonNumber number -> out.append(number.lexical());
      case WorldlineJson.JsonFlag flag -> out.append(flag.value());
      case WorldlineJson.JsonNothing _ -> out.append("null");
      case WorldlineJson.JsonObject object -> writeObject(object, out);
      case WorldlineJson.JsonArray array -> writeArray(array, out);
    }
  }

  private static void writeArray(WorldlineJson.JsonArray array, StringBuilder out) {
    out.append('[');
    boolean first = true;
    for (WorldlineJson element : array.elements()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      write(element, out);
    }
    out.append(']');
  }

  private static void writeObject(WorldlineJson.JsonObject object, StringBuilder out) {
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, WorldlineJson> member : object.members().entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeString(member.getKey(), out);
      out.append(':');
      write(member.getValue(), out);
    }
    out.append('}');
  }

  private static void writeString(String value, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char one = value.charAt(i);
      switch (one) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (one < 0x20 || one > HIGHEST_UNESCAPED) {
            out.append(String.format("\\u%04x", (int) one));
          } else {
            out.append(one);
          }
        }
      }
    }
    out.append('"');
  }
}
