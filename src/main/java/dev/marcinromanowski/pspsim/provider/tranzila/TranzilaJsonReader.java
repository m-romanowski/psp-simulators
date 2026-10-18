package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TranzilaJsonReader {

  private final String source;
  private int at;

  private TranzilaJsonReader(String source) {
    this.source = source;
    this.at = 0;
  }

  static Optional<TranzilaJson.JsonObject> objectIn(String source) {
    TranzilaJsonReader reader = new TranzilaJsonReader(source);
    try {
      TranzilaJson read = reader.value();
      reader.skipBlanks();
      if (reader.at != source.length() || !(read instanceof TranzilaJson.JsonObject object)) {
        return Optional.empty();
      }
      return Optional.of(object);
    } catch (TranzilaJsonUnreadable unreadable) {
      return Optional.empty();
    }
  }

  private TranzilaJson value() {
    skipBlanks();
    char next = peek();
    return switch (next) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> new TranzilaJson.JsonText(string());
      case 't' -> keyword("true", new TranzilaJson.JsonFlag(true));
      case 'f' -> keyword("false", new TranzilaJson.JsonFlag(false));
      case 'n' -> keyword("null", new TranzilaJson.JsonNothing());
      default -> number();
    };
  }

  private TranzilaJson.JsonObject object() {
    take('{');
    Map<String, TranzilaJson> members = new LinkedHashMap<>();
    skipBlanks();
    if (peek() == '}') {
      at++;
      return new TranzilaJson.JsonObject(members);
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
        return new TranzilaJson.JsonObject(members);
      }
      if (separator != ',') {
        throw new TranzilaJsonUnreadable();
      }
    }
  }

  private TranzilaJson.JsonArray array() {
    take('[');
    List<TranzilaJson> elements = new ArrayList<>();
    skipBlanks();
    if (peek() == ']') {
      at++;
      return new TranzilaJson.JsonArray(elements);
    }
    while (true) {
      elements.add(value());
      skipBlanks();
      char separator = peek();
      at++;
      if (separator == ']') {
        return new TranzilaJson.JsonArray(elements);
      }
      if (separator != ',') {
        throw new TranzilaJsonUnreadable();
      }
    }
  }

  private TranzilaJson keyword(String spelled, TranzilaJson read) {
    if (!source.startsWith(spelled, at)) {
      throw new TranzilaJsonUnreadable();
    }
    at += spelled.length();
    return read;
  }

  private TranzilaJson.JsonNumber number() {
    return new TranzilaJson.JsonNumber(numberLexical());
  }

  static boolean isANumber(String lexical) {
    TranzilaJsonReader reader = new TranzilaJsonReader(lexical);
    try {
      reader.numberLexical();
    } catch (TranzilaJsonUnreadable notANumber) {
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
      throw new TranzilaJsonUnreadable();
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
        default -> throw new TranzilaJsonUnreadable();
      }
    }
  }

  private char codePoint() {
    if (at + 4 > source.length()) {
      throw new TranzilaJsonUnreadable();
    }
    String hex = source.substring(at, at + 4);
    at += 4;
    try {
      return (char) Integer.parseInt(hex, 16);
    } catch (NumberFormatException notHex) {
      throw new TranzilaJsonUnreadable();
    }
  }

  private void take(char expected) {
    if (peek() != expected) {
      throw new TranzilaJsonUnreadable();
    }
    at++;
  }

  private char peek() {
    if (at >= source.length()) {
      throw new TranzilaJsonUnreadable();
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

  private static final class TranzilaJsonUnreadable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TranzilaJsonUnreadable() {
      super(null, null, false, false);
    }
  }
}
