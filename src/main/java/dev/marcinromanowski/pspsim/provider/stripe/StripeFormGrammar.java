package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class StripeFormGrammar {

  static final String A_NAME_IS_A_WORD_AND_BRACKETED_KEYS =
      "a parameter name is a word followed by bracketed keys, such as amount,"
          + " metadata[order_id], expand[] or expand[0], and this name is not one";

  static final String AN_APPENDED_ELEMENT_ENDS_THE_NAME =
      "[] appends one value to a list and must end the name, because a key after it would not"
          + " say which element it belongs to. A list of hashes is sent as name[0][key] and"
          + " name[1][key]";

  static final String ONE_PARAMETER_IN_TWO_SHAPES =
      "the request sends this parameter in two shapes at once, as a value, as a list or as a"
          + " hash, and only one of them can be read. A list is sent either as name[]=... once per"
          + " element or as name[0]=..., name[1]=..., never as both";

  static final String ONE_VALUE_SENT_TWICE =
      "the request sends a value for this parameter more than once, and one value cannot be read"
          + " out of two";

  private static final char OPENS = '[';

  private static final char CLOSES = ']';

  private static final String APPENDS = "";

  private StripeFormGrammar() {}

  record Decoded(
      SequencedMap<String, StripeParameter> parameters, @Nullable StripeError unreadable) {}

  static Decoded decoded(SequencedMap<String, List<String>> wire) {
    AHash root = new AHash(new LinkedHashMap<>());
    for (Map.Entry<String, List<String>> field : wire.entrySet()) {
      for (String value : field.getValue()) {
        StripeError unreadable = placed(root, field.getKey(), value);
        if (unreadable != null) {
          return new Decoded(new LinkedHashMap<>(), unreadable);
        }
      }
    }
    LinkedHashMap<String, StripeParameter> parameters = new LinkedHashMap<>();
    root.entries().forEach((name, node) -> parameters.put(name, frozen(node)));
    return new Decoded(parameters, null);
  }

  private static @Nullable StripeError placed(AHash root, String key, String value) {
    List<String> steps = stepsOf(key);
    if (steps == null) {
      return StripeErrors.unreadableEncoding(
          key.isEmpty() ? null : key, A_NAME_IS_A_WORD_AND_BRACKETED_KEYS);
    }
    if (steps.subList(0, steps.size() - 1).contains(APPENDS)) {
      return StripeErrors.unreadableEncoding(key, AN_APPENDED_ELEMENT_ENDS_THE_NAME);
    }
    AHash container = root;
    for (int at = 0; at < steps.size() - 1; at++) {
      Node existing = container.entries().get(steps.get(at));
      if (APPENDS.equals(steps.get(at + 1))) {
        return appended(container, steps, at, existing, value);
      }
      if (existing == null) {
        AHash hash = new AHash(new LinkedHashMap<>());
        container.entries().put(steps.get(at), hash);
        container = hash;
      } else if (existing instanceof AHash hash) {
        container = hash;
      } else {
        return StripeErrors.unreadableEncoding(pathOf(steps, at), ONE_PARAMETER_IN_TWO_SHAPES);
      }
    }
    int leaf = steps.size() - 1;
    Node existing = container.entries().get(steps.get(leaf));
    if (existing == null) {
      container.entries().put(steps.get(leaf), new Leaf(value));
      return null;
    }
    return StripeErrors.unreadableEncoding(
        pathOf(steps, leaf),
        existing instanceof Leaf ? ONE_VALUE_SENT_TWICE : ONE_PARAMETER_IN_TWO_SHAPES);
  }

  private static @Nullable StripeError appended(
      AHash container, List<String> steps, int at, @Nullable Node existing, String value) {
    if (existing == null) {
      List<String> list = new ArrayList<>();
      list.add(value);
      container.entries().put(steps.get(at), new AList(list));
      return null;
    }
    if (existing instanceof AList list) {
      list.values().add(value);
      return null;
    }
    return StripeErrors.unreadableEncoding(pathOf(steps, at), ONE_PARAMETER_IN_TWO_SHAPES);
  }

  private static @Nullable List<String> stepsOf(String key) {
    int opens = key.indexOf(OPENS);
    String name = opens < 0 ? key : key.substring(0, opens);
    if (name.isEmpty() || name.indexOf(CLOSES) >= 0) {
      return null;
    }
    List<String> steps = new ArrayList<>();
    steps.add(name);
    int at = opens < 0 ? key.length() : opens;
    while (at < key.length()) {
      int closes = key.indexOf(CLOSES, at);
      if (key.charAt(at) != OPENS || closes < 0) {
        return null;
      }
      String segment = key.substring(at + 1, closes);
      if (segment.indexOf(OPENS) >= 0) {
        return null;
      }
      steps.add(segment);
      at = closes + 1;
    }
    return steps;
  }

  private static String pathOf(List<String> steps, int through) {
    StringBuilder path = new StringBuilder(steps.get(0));
    for (int at = 1; at <= through; at++) {
      path.append(OPENS).append(steps.get(at)).append(CLOSES);
    }
    return path.toString();
  }

  private static StripeParameter frozen(Node node) {
    return switch (node) {
      case Leaf leaf -> new StripeParameter.Scalar(leaf.value());
      case AList list -> new StripeParameter.Appended(list.values());
      case AHash hash -> {
        LinkedHashMap<String, StripeParameter> entries = new LinkedHashMap<>();
        hash.entries().forEach((key, entry) -> entries.put(key, frozen(entry)));
        yield new StripeParameter.Hashed(entries);
      }
    };
  }

  private sealed interface Node permits Leaf, AList, AHash {}

  private record Leaf(String value) implements Node {}

  private record AList(List<String> values) implements Node {}

  private record AHash(LinkedHashMap<String, Node> entries) implements Node {}
}
