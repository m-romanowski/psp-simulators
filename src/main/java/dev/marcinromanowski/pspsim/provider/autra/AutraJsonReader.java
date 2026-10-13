package dev.marcinromanowski.pspsim.provider.autra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AutraJsonReader {

  private final String source;
  private int at;

  private AutraJsonReader(String source) {
    this.source = source;
    this.at = 0;
  }

  static Optional<AutraJson.JsonObject> objectIn(String source) {
    AutraJsonReader reader = new AutraJsonReader(source);
    try {
      AutraJson read = reader.value();
      reader.skipBlanks();
      if (reader.at != source.length() || !(read instanceof AutraJson.JsonObject object)) {
        return Optional.empty();
      }
      return Optional.of(object);
    } catch (AutraJsonUnreadable unreadable) {
      return Optional.empty();
    }
  }

  private AutraJson value() {
    skipBlanks();
    char next = peek();
    return switch (next) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> new AutraJson.JsonText(string());
      case 't' -> keyword("true", new AutraJson.JsonFlag(true));
      case 'f' -> keyword("false", new AutraJson.JsonFlag(false));
      case 'n' -> keyword("null", new AutraJson.JsonNothing());
      default -> number();
    };
  }

  private AutraJson.JsonObject object() {
    take('{');
    Map<String, AutraJson> members = new LinkedHashMap<>();
    skipBlanks();
    if (peek() == '}') {
      at++;
      return new AutraJson.JsonObject(members);
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
        return new AutraJson.JsonObject(members);
      }
      if (separator != ',') {
        throw new AutraJsonUnreadable();
      }
    }
  }

  private AutraJson.JsonArray array() {
    take('[');
    List<AutraJson> elements = new ArrayList<>();
    skipBlanks();
    if (peek() == ']') {
      at++;
      return new AutraJson.JsonArray(elements);
    }
    while (true) {
      elements.add(value());
      skipBlanks();
      char separator = peek();
      at++;
      if (separator == ']') {
        return new AutraJson.JsonArray(elements);
      }
      if (separator != ',') {
        throw new AutraJsonUnreadable();
      }
    }
  }

  private AutraJson keyword(String spelled, AutraJson read) {
    if (!source.startsWith(spelled, at)) {
      throw new AutraJsonUnreadable();
    }
    at += spelled.length();
    return read;
  }

  private AutraJson.JsonNumber number() {
    return new AutraJson.JsonNumber(numberLexical());
  }

  static boolean isANumber(String lexical) {
    AutraJsonReader reader = new AutraJsonReader(lexical);
    try {
      reader.numberLexical();
    } catch (AutraJsonUnreadable notANumber) {
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
      throw new AutraJsonUnreadable();
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
        default -> throw new AutraJsonUnreadable();
      }
    }
  }

  private char codePoint() {
    if (at + 4 > source.length()) {
      throw new AutraJsonUnreadable();
    }
    String hex = source.substring(at, at + 4);
    at += 4;
    try {
      return (char) Integer.parseInt(hex, 16);
    } catch (NumberFormatException notHex) {
      throw new AutraJsonUnreadable();
    }
  }

  private void take(char expected) {
    if (peek() != expected) {
      throw new AutraJsonUnreadable();
    }
    at++;
  }

  private char peek() {
    if (at >= source.length()) {
      throw new AutraJsonUnreadable();
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

  private static final class AutraJsonUnreadable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    AutraJsonUnreadable() {
      super(null, null, false, false);
    }
  }
}
