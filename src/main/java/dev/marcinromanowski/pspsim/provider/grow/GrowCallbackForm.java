package dev.marcinromanowski.pspsim.provider.grow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GrowCallbackForm {

  private static final String UNRESERVED = "-_.~";

  private final List<String> names = new ArrayList<>();
  private final List<String> values = new ArrayList<>();

  void put(String name, String value) {
    names.add(name);
    values.add(value);
  }

  byte[] toBytes() {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < names.size(); i++) {
      if (i > 0) {
        body.append('&');
      }
      body.append(encode(names.get(i))).append('=').append(encode(values.get(i)));
    }
    return body.toString().getBytes(StandardCharsets.UTF_8);
  }

  Map<String, List<String>> forAudit() {
    Map<String, List<String>> body = new LinkedHashMap<>();
    for (int i = 0; i < names.size(); i++) {
      body.computeIfAbsent(names.get(i), name -> new ArrayList<>()).add(values.get(i));
    }
    return body;
  }

  static String encode(String text) {
    StringBuilder encoded = new StringBuilder();
    for (byte raw : text.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (raw & 0xFF);
      if (isUnreserved(c)) {
        encoded.append(c);
      } else {
        encoded.append('%').append(HEX[(raw >> 4) & 0xF]).append(HEX[raw & 0xF]);
      }
    }
    return encoded.toString();
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || UNRESERVED.indexOf(c) >= 0;
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
}
