package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CannedTransport implements CheckTransport {

  private final Map<String, List<CheckResponse>> answers = new LinkedHashMap<>();
  private final Map<String, Integer> served = new LinkedHashMap<>();
  private final List<CheckRequest> sent = new ArrayList<>();

  public CannedTransport answering(String method, String pathPrefix, CheckResponse answer) {
    answers.put(method + " " + pathPrefix, List.of(answer));
    return this;
  }

  public CannedTransport answeringInTurn(
      String method, String pathPrefix, CheckResponse first, CheckResponse then) {
    answers.put(method + " " + pathPrefix, List.of(first, then));
    return this;
  }

  public CannedTransport answering(String method, String pathPrefix, int status, String body) {
    return answering(method, pathPrefix, new CheckResponse(status, Map.of(), body));
  }

  public List<CheckRequest> sent() {
    return List.copyOf(sent);
  }

  @Override
  public CheckResponse send(CheckRequest request) {
    sent.add(request);
    String chosen = null;
    int longest = -1;
    for (String key : answers.keySet()) {
      String prefix = key.substring(key.indexOf(' ') + 1);
      String method = key.substring(0, key.indexOf(' '));
      if (method.equals(request.method())
          && request.path().startsWith(prefix)
          && prefix.length() > longest) {
        chosen = key;
        longest = prefix.length();
      }
    }
    if (chosen == null) {
      throw new IllegalStateException(
          "no canned answer for " + request.method() + " " + request.path());
    }
    List<CheckResponse> inTurn = answers.get(chosen);
    int turn = served.merge(chosen, 1, Integer::sum) - 1;
    return inTurn.get(Math.min(turn, inTurn.size() - 1));
  }
}
