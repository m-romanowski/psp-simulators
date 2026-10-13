package dev.marcinromanowski.pspsim.provider.stripe;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

final class StripeJsonWriter {

  private StripeJsonWriter() {}

  static byte[] toBytes(StripeJson value) {
    return toText(value).getBytes(StandardCharsets.UTF_8);
  }

  static String toText(StripeJson value) {
    StringBuilder out = new StringBuilder();
    write(value, out);
    return out.toString();
  }

  private static void write(StripeJson value, StringBuilder out) {
    switch (value) {
      case StripeJson.JsonText text -> writeString(text.value(), out);
      case StripeJson.JsonRawNumber number -> out.append(number.lexicalForm());
      case StripeJson.JsonBoolean flag -> out.append(flag.value());
      case StripeJson.JsonNull _ -> out.append("null");
      case StripeJson.JsonObject object -> writeObject(object, out);
      case StripeJson.JsonArray array -> writeArray(array, out);
    }
  }

  private static void writeArray(StripeJson.JsonArray array, StringBuilder out) {
    out.append('[');
    boolean first = true;
    for (StripeJson element : array.elements()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      write(element, out);
    }
    out.append(']');
  }

  private static void writeObject(StripeJson.JsonObject object, StringBuilder out) {
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, StripeJson> member : object.members().entrySet()) {
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
          if (c < 0x20) {
            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
