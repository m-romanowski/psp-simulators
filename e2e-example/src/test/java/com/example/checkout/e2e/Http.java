package com.example.checkout.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Http {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  private final String baseUrl;

  Http(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  record Answer(int status, String body, Map<String, List<String>> headers) {

    JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (IOException notJson) {
        throw new IllegalStateException(
            "expected a JSON body and read " + preview() + ": " + notJson.getMessage(), notJson);
      }
    }

    String text(String path) {
      return Optional.ofNullable(at(path))
          .filter(node -> !node.isNull())
          .map(JsonNode::asText)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "the answer carries nothing at " + path + "; it reads " + preview()));
    }

    JsonNode at(String path) {
      JsonNode node = json();
      for (String step : path.split("\\.")) {
        if (node == null) {
          return null;
        }
        node = step.matches("\\d+") ? node.get(Integer.parseInt(step)) : node.get(step);
      }
      return node;
    }

    String contentType() {
      return header("Content-Type").orElse("");
    }

    Optional<String> header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .flatMap(entry -> entry.getValue().stream())
          .findFirst();
    }

    String preview() {
      String rendered = body == null ? "" : body;
      return "HTTP "
          + status
          + " "
          + (rendered.length() > 600 ? rendered.substring(0, 600) + "..." : rendered);
    }
  }

  Answer get(String path) {
    return send(HttpRequest.newBuilder(uri(path)).GET());
  }

  Answer delete(String path) {
    return send(HttpRequest.newBuilder(uri(path)).DELETE());
  }

  Answer postJson(String path, String body) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
  }

  Answer putJson(String path, String body) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
  }

  Answer postForm(String path, Map<String, String> fields, Map<String, String> headers) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(fields), StandardCharsets.UTF_8));
    headers.forEach(builder::header);
    return send(builder);
  }

  static String form(Map<String, String> fields) {
    StringBuilder encoded = new StringBuilder();
    fields.forEach(
        (name, value) -> {
          if (value == null) {
            return;
          }
          if (!encoded.isEmpty()) {
            encoded.append('&');
          }
          encoded
              .append(java.net.URLEncoder.encode(name, StandardCharsets.UTF_8))
              .append('=')
              .append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
    return encoded.toString();
  }

  static String jsonOf(Map<String, ?> fields) {
    try {
      return JSON.writeValueAsString(fields);
    } catch (IOException unwritable) {
      throw new IllegalStateException("could not render " + fields + " as JSON", unwritable);
    }
  }

  static Map<String, Object> fields(Object... pairs) {
    Map<String, Object> rendered = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      rendered.put((String) pairs[index], pairs[index + 1]);
    }
    return rendered;
  }

  private Answer send(HttpRequest.Builder builder) {
    HttpRequest request = builder.timeout(Duration.ofSeconds(60)).build();
    try {
      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      return new Answer(response.statusCode(), response.body(), response.headers().map());
    } catch (IOException unreachable) {
      throw new TransportBroke(request.method() + " " + request.uri(), unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted calling " + request.uri(), interrupted);
    }
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  static final class TransportBroke extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TransportBroke(String what, Throwable cause) {
      super("the transport broke on " + what + ": " + cause, cause);
    }
  }
}
