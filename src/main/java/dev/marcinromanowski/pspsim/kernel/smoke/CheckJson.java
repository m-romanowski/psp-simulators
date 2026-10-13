package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CheckJson {

  static final String NOT_THE_JSON_THIS_ROUTE_PUBLISHES = "the answer is not the JSON it publishes";

  private static final int CODE_POINT_DIGITS = 4;

  private static final int CODE_POINT_RADIX = 16;

  public @Nullable String at(String body, String pointer) {
    Object found = read(body);
    for (String step : pointer.split("/", -1)) {
      if (step.isEmpty()) {
        continue;
      }
      found = member(found, step);
      if (found == null) {
        return null;
      }
    }
    return found instanceof String text ? text : null;
  }

  public @Nullable Object read(String body) {
    Cursor cursor = new Cursor(body);
    Object value = cursor.value();
    cursor.whitespace();
    if (!cursor.done()) {
      throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
    }
    return value;
  }

  private static @Nullable Object member(@Nullable Object holder, String step) {
    if (holder instanceof Map<?, ?> object) {
      return object.get(step);
    }
    if (holder instanceof List<?> array) {
      int index = indexOf(step, array.size());
      return index < 0 ? null : array.get(index);
    }
    return null;
  }

  private static int indexOf(String step, int members) {
    int index = 0;
    for (int at = 0; at < step.length(); at++) {
      int digit = Character.digit(step.charAt(at), 10);
      if (digit < 0) {
        return -1;
      }
      index = index * 10 + digit;
      if (index >= members) {
        return -1;
      }
    }
    return index;
  }

  private static final class Cursor {

    private final String source;
    private int at;

    private Cursor(String source) {
      this.source = source;
    }

    private boolean done() {
      return at >= source.length();
    }

    private void whitespace() {
      while (!done() && Character.isWhitespace(source.charAt(at))) {
        at++;
      }
    }

    private char peek() {
      if (done()) {
        throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
      }
      return source.charAt(at);
    }

    private void expect(char wanted) {
      if (peek() != wanted) {
        throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
      }
      at++;
    }

    private @Nullable Object value() {
      whitespace();
      char opening = peek();
      return switch (opening) {
        case '{' -> object();
        case '[' -> array();
        case '"' -> string();
        case 't' -> literal("true", "true");
        case 'f' -> literal("false", "false");
        case 'n' -> literal("null", null);
        default -> number();
      };
    }

    private Map<String, Object> object() {
      expect('{');
      Map<String, Object> members = new LinkedHashMap<>();
      whitespace();
      if (peek() != '}') {
        readMembersInto(members);
      }
      expect('}');
      return members;
    }

    private void readMembersInto(Map<String, Object> members) {
      while (true) {
        whitespace();
        String name = string();
        whitespace();
        expect(':');
        Object held = value();
        if (held != null) {
          members.put(name, held);
        }
        whitespace();
        if (peek() != ',') {
          return;
        }
        at++;
      }
    }

    private List<Object> array() {
      expect('[');
      List<Object> members = new ArrayList<>();
      whitespace();
      if (peek() != ']') {
        readElementsInto(members);
      }
      expect(']');
      return members;
    }

    private void readElementsInto(List<Object> members) {
      while (true) {
        Object held = value();
        members.add(held == null ? "" : held);
        whitespace();
        if (peek() != ',') {
          return;
        }
        at++;
      }
    }

    private String string() {
      expect('"');
      StringBuilder read = new StringBuilder();
      while (peek() != '"') {
        char next = source.charAt(at++);
        if (next != '\\') {
          read.append(next);
          continue;
        }
        read.append(escaped());
      }
      at++;
      return read.toString();
    }

    private char escaped() {
      char marker = peek();
      at++;
      return switch (marker) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case 'r' -> '\r';
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'u' -> codePoint();
        default -> marker;
      };
    }

    private char codePoint() {
      int value = 0;
      for (int read = 0; read < CODE_POINT_DIGITS; read++) {
        int digit = Character.digit(peek(), CODE_POINT_RADIX);
        if (digit < 0) {
          throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
        }
        value = value * CODE_POINT_RADIX + digit;
        at++;
      }
      return (char) value;
    }

    private @Nullable String literal(String spelling, @Nullable String held) {
      if (!source.startsWith(spelling, at)) {
        throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
      }
      at += spelling.length();
      return held;
    }

    private String number() {
      int opened = at;
      while (!done() && "-+.eE0123456789".indexOf(source.charAt(at)) >= 0) {
        at++;
      }
      if (opened == at) {
        throw new IllegalStateException(NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
      }
      return source.substring(opened, at);
    }
  }
}
