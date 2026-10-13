package com.example.tester.ui;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TheProviderApi {

  private final HttpClient http =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .connectTimeout(Duration.ofSeconds(10))
          .build();
  private final List<String> refused = new CopyOnWriteArrayList<>();
  private final List<String> answers = new CopyOnWriteArrayList<>();

  public HttpResponse<String> send(HttpRequest request) {
    if (ATesterRoute.isNamedBy(request.uri().toString())) {
      refused.add(request.method() + " " + request.uri());
      throw new IllegalStateException(
          "a shop's server knows only the provider's own API, so it cannot call "
              + request.method()
              + " "
              + request.uri());
    }
    try {
      HttpResponse<String> answer =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      answers.add(
          request.method()
              + " "
              + request.uri()
              + " answered "
              + answer.statusCode()
              + " "
              + answer.headers().map()
              + " "
              + answer.body());
      return answer;
    } catch (IOException unreachable) {
      throw new IllegalStateException(
          "the simulator did not answer " + request.method() + " " + request.uri(), unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while calling " + request.uri());
    }
  }

  public List<String> everyAnswerItGot() {
    return List.copyOf(answers);
  }

  public List<String> whatItRefusedToCall() {
    return List.copyOf(refused);
  }
}
