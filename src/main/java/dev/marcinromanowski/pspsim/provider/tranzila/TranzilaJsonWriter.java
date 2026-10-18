package dev.marcinromanowski.pspsim.provider.tranzila;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class TranzilaJsonWriter {

  private static final char HIGHEST_UNESCAPED = 0x7e;

  private TranzilaJsonWriter() {}

  static byte[] toBytes(TranzilaJson value) {
    return toText(value).getBytes(StandardCharsets.UTF_8);
  }

  static String toText(TranzilaJson value) {
    StringBuilder out = new StringBuilder();
    write(value, out);
    return out.toString();
  }

  private static void write(TranzilaJson value, StringBuilder out) {
    switch (value) {
      case TranzilaJson.JsonText text -> writeString(text.value(), out);
      case TranzilaJson.JsonNumber number -> out.append(number.lexical());
      case TranzilaJson.JsonFlag flag -> out.append(flag.value());
      case TranzilaJson.JsonNothing _ -> out.append("null");
      case TranzilaJson.JsonObject object -> writeObject(object, out);
      case TranzilaJson.JsonArray array -> writeArray(array, out);
    }
  }

  private static void writeArray(TranzilaJson.JsonArray array, StringBuilder out) {
    out.append('[');
    boolean first = true;
    for (TranzilaJson element : array.elements()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      write(element, out);
    }
    out.append(']');
  }

  private static void writeObject(TranzilaJson.JsonObject object, StringBuilder out) {
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, TranzilaJson> member : object.members().entrySet()) {
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
