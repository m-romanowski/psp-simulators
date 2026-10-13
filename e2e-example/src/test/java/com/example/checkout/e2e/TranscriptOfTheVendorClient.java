package com.example.checkout.e2e;

import com.stripe.exception.StripeException;
import com.stripe.net.HttpClient;
import com.stripe.net.HttpContent;
import com.stripe.net.HttpURLConnectionClient;
import com.stripe.net.StripeRequest;
import com.stripe.net.StripeResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranscriptOfTheVendorClient extends HttpClient {

  private static final String WITHHELD = "<withheld>";

  private static final List<String> SECRET_HEADERS = List.of("authorization");

  private static final List<String> SECRET_PROPERTIES =
      List.of("client_secret", "secret", "secretKey");

  private final HttpClient delegate = new HttpURLConnectionClient();
  private final List<String> exchanges = new ArrayList<>();
  private final Set<String> contentTypesTheVendorClientRead = new LinkedHashSet<>();
  private final Set<String> authenticationSchemesItSent = new LinkedHashSet<>();
  private String contentTypeOfTheLastAnswer = "";

  List<String> exchanges() {
    return List.copyOf(exchanges);
  }

  Set<String> contentTypesTheVendorClientRead() {
    return Set.copyOf(contentTypesTheVendorClientRead);
  }

  Set<String> authenticationSchemesItSent() {
    return Set.copyOf(authenticationSchemesItSent);
  }

  String contentTypeOfTheLastAnswer() {
    return contentTypeOfTheLastAnswer;
  }

  String rendered() {
    return String.join("", exchanges);
  }

  @Override
  public StripeResponse request(StripeRequest request) throws StripeException {
    StringBuilder record = new StringBuilder(256);
    record.append("--> ").append(request.method()).append(' ').append(request.url()).append('\n');
    request
        .headers()
        .map()
        .forEach(
            (name, values) ->
                values.forEach(
                    value -> record.append("    ").append(header(name, value)).append('\n')));
    request
        .headers()
        .firstValue("Authorization")
        .ifPresent(
            sent -> {
              int space = sent.indexOf(' ');
              authenticationSchemesItSent.add(space < 0 ? sent : sent.substring(0, space));
            });
    HttpContent content = request.content();
    if (content != null) {
      record
          .append("    Content-Type: ")
          .append(content.contentType())
          .append("\n    body: ")
          .append(withheldIn(new String(content.byteArrayContent(), StandardCharsets.UTF_8)))
          .append('\n');
    }
    StripeResponse response;
    try {
      response = delegate.request(request);
    } catch (StripeException nothingCameBack) {
      record
          .append("<-- nothing came back: ")
          .append(nothingCameBack.getClass().getSimpleName())
          .append('\n');
      exchanges.add(record.toString());
      throw nothingCameBack;
    }
    response
        .headers()
        .firstValue("Content-Type")
        .ifPresent(
            type -> {
              contentTypesTheVendorClientRead.add(type);
              contentTypeOfTheLastAnswer = type;
            });
    record
        .append("<-- ")
        .append(response.code())
        .append("\n    body: ")
        .append(withheldIn(response.body()))
        .append('\n');
    exchanges.add(record.toString());
    return response;
  }

  private static String header(String name, String value) {
    if (name == null) {
      return "(status line): " + value;
    }
    if (SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
      int space = value == null ? -1 : value.indexOf(' ');
      return name + ": " + (space < 0 ? "" : value.substring(0, space) + " ") + WITHHELD;
    }
    return name + ": " + value;
  }

  private static String withheldIn(String body) {
    String redacted = body == null ? "" : body;
    for (String property : SECRET_PROPERTIES) {
      redacted =
          Pattern.compile("(\"" + Pattern.quote(property) + "\"\\s*:\\s*)\"[^\"]*\"")
              .matcher(redacted)
              .replaceAll("$1\"" + Matcher.quoteReplacement(WITHHELD) + "\"");
      redacted =
          Pattern.compile("(^|&)(" + Pattern.quote(property) + "=)[^&]*")
              .matcher(redacted)
              .replaceAll("$1$2" + WITHHELD);
    }
    return redacted;
  }
}
