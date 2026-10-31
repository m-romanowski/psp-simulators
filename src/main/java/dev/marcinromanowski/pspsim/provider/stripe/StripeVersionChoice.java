package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

sealed interface StripeVersionChoice {

  record AnsweredAt(StripeApiVersion version) implements StripeVersionChoice {}

  record Refused(StripeError error) implements StripeVersionChoice {}

  static StripeVersionChoice forTheAccount(
      Map<String, List<String>> headers, StripeApiVersion accountDefault) {
    String sent = sentIn(headers);
    if (sent == null) {
      return new AnsweredAt(accountDefault);
    }
    return named(sent);
  }

  static StripeVersionChoice admittingTheMobileSdks(
      Map<String, List<String>> headers, StripeApiVersion accountDefault) {
    String sent = sentIn(headers);
    if (sent == null) {
      return new AnsweredAt(accountDefault);
    }
    StripeApiVersion asSent = new StripeApiVersion(sent);
    if (StripeApiVersions.PINNED_BY_THE_MOBILE_SDKS.contains(asSent)) {
      return new AnsweredAt(asSent);
    }
    return named(sent);
  }

  private static StripeVersionChoice named(String sent) {
    return StripeApiVersions.answeredNamed(sent)
        .<StripeVersionChoice>map(AnsweredAt::new)
        .orElseGet(() -> new Refused(refusalOf(sent)));
  }

  private static StripeError refusalOf(String sent) {
    if (StripeApiVersions.isPublished(sent)) {
      return StripeErrors.versionNotAnswered(sent);
    }
    if (StripeApiVersions.isLaterThanTheCatalogue(sent)) {
      return StripeErrors.versionLaterThanTheCatalogue(sent);
    }
    int beta = sent.indexOf(StripeApiVersions.BETA_SEPARATOR);
    if (beta >= 0 && namesARelease(sent.substring(0, beta).strip())) {
      return StripeErrors.versionAsksForABeta(sent);
    }
    return StripeErrors.versionUnrecognised(sent);
  }

  private static boolean namesARelease(String version) {
    return StripeApiVersions.isPublished(version)
        || StripeApiVersions.isLaterThanTheCatalogue(version);
  }

  static @Nullable String sentIn(Map<String, List<String>> headers) {
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      if (StripeFields.STRIPE_VERSION_HEADER.equalsIgnoreCase(header.getKey())) {
        for (String sent : header.getValue()) {
          if (!sent.isBlank()) {
            return sent.strip();
          }
        }
      }
    }
    return null;
  }
}
