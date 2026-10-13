package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class WorldlineJsonReader {

  private final String source;
  private int at;

  private WorldlineJsonReader(String source) {
    this.source = source;
    this.at = 0;
  }

  static Optional<WorldlineJson.JsonObject> objectIn(String source) {
    WorldlineJsonReader reader = new WorldlineJsonReader(source);
    try {
      WorldlineJson read = reader.value();
      reader.skipBlanks();
      if (reader.at != source.length() || !(read instanceof WorldlineJson.JsonObject object)) {
        return Optional.empty();
      }
      return Optional.of(object);
    } catch (WorldlineJsonUnreadable unreadable) {
      return Optional.empty();
    }
  }

  private WorldlineJson value() {
    skipBlanks();
    char next = peek();
    return switch (next) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> new WorldlineJson.JsonText(string());
      case 't' -> keyword("true", new WorldlineJson.JsonFlag(true));
      case 'f' -> keyword("false", new WorldlineJson.JsonFlag(false));
      case 'n' -> keyword("null", new WorldlineJson.JsonNothing());
      default -> number();
    };
  }

  private WorldlineJson.JsonObject object() {
    take('{');
    Map<String, WorldlineJson> members = new LinkedHashMap<>();
    skipBlanks();
    if (peek() == '}') {
      at++;
      return new WorldlineJson.JsonObject(members);
    }
    while (true) {
      skipBlanks();
      String name = string();
      skipBlanks();
      take(':');
      members.put(name, value());
      skipBlanks();
      char separator = peek();
      at++;
      if (separator == '}') {
        return new WorldlineJson.JsonObject(members);
      }
      if (separator != ',') {
        throw new WorldlineJsonUnreadable();
      }
    }
  }

  private WorldlineJson.JsonArray array() {
    take('[');
    List<WorldlineJson> elements = new ArrayList<>();
    skipBlanks();
    if (peek() == ']') {
      at++;
      return new WorldlineJson.JsonArray(elements);
    }
    while (true) {
      elements.add(value());
      skipBlanks();
      char separator = peek();
      at++;
      if (separator == ']') {
        return new WorldlineJson.JsonArray(elements);
      }
      if (separator != ',') {
        throw new WorldlineJsonUnreadable();
      }
    }
  }

  private WorldlineJson keyword(String spelled, WorldlineJson read) {
    if (!source.startsWith(spelled, at)) {
      throw new WorldlineJsonUnreadable();
    }
    at += spelled.length();
    return read;
  }

  private WorldlineJson.JsonNumber number() {
    return new WorldlineJson.JsonNumber(numberLexical());
  }

  static boolean isANumber(String lexical) {
    WorldlineJsonReader reader = new WorldlineJsonReader(lexical);
    try {
      reader.numberLexical();
    } catch (WorldlineJsonUnreadable notANumber) {
      return false;
    }
    return reader.at == lexical.length();
  }

  private String numberLexical() {
    int from = at;
    takeIfPresent('-');
    takeTheIntegerPart();
    if (takeIfPresent('.')) {
      takeDigits();
    }
    if (takeIfPresent('e') || takeIfPresent('E')) {
      if (!takeIfPresent('+')) {
        takeIfPresent('-');
      }
      takeDigits();
    }
    return source.substring(from, at);
  }

  private void takeTheIntegerPart() {
    if (peek() == '0') {
      at++;
      return;
    }
    takeDigits();
  }

  private void takeDigits() {
    int from = at;
    while (at < source.length() && isADigit(source.charAt(at))) {
      at++;
    }
    if (at == from) {
      throw new WorldlineJsonUnreadable();
    }
  }

  private boolean takeIfPresent(char expected) {
    if (at < source.length() && source.charAt(at) == expected) {
      at++;
      return true;
    }
    return false;
  }

  private static boolean isADigit(char one) {
    return one >= '0' && one <= '9';
  }

  private String string() {
    take('"');
    StringBuilder read = new StringBuilder();
    while (true) {
      char one = peek();
      at++;
      if (one == '"') {
        return read.toString();
      }
      if (one != '\\') {
        read.append(one);
        continue;
      }
      char escaped = peek();
      at++;
      switch (escaped) {
        case '"' -> read.append('"');
        case '\\' -> read.append('\\');
        case '/' -> read.append('/');
        case 'b' -> read.append('\b');
        case 'f' -> read.append('\f');
        case 'n' -> read.append('\n');
        case 'r' -> read.append('\r');
        case 't' -> read.append('\t');
        case 'u' -> read.append(codePoint());
        default -> throw new WorldlineJsonUnreadable();
      }
    }
  }

  private char codePoint() {
    if (at + 4 > source.length()) {
      throw new WorldlineJsonUnreadable();
    }
    String hex = source.substring(at, at + 4);
    at += 4;
    try {
      return (char) Integer.parseInt(hex, 16);
    } catch (NumberFormatException notHex) {
      throw new WorldlineJsonUnreadable();
    }
  }

  private void take(char expected) {
    if (peek() != expected) {
      throw new WorldlineJsonUnreadable();
    }
    at++;
  }

  private char peek() {
    if (at >= source.length()) {
      throw new WorldlineJsonUnreadable();
    }
    return source.charAt(at);
  }

  private void skipBlanks() {
    while (at < source.length() && isBlank(source.charAt(at))) {
      at++;
    }
  }

  private static boolean isBlank(char one) {
    return one == ' ' || one == '\t' || one == '\n' || one == '\r';
  }

  private static final class WorldlineJsonUnreadable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    WorldlineJsonUnreadable() {
      super(null, null, false, false);
    }
  }
}
