package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CreateHostedTokenization {

  static final int SESSION_ATTEMPTS = 5;

  static final String THE_PUBLISHED_FORM_PATH = "/hostedtokenization/tokenization/form/";

  static final String THE_PARTIAL_URL_IS_REPRODUCED_AS_PUBLISHED =
      "provider worldline publishes partialRedirectUrl as deprecated and populated at the same"
          + " time: the schema marks it deprecated with x-deprecated-by hostedTokenizationUrl and"
          + " says \"Deprecated. Use hostedTokenizationUrl instead.\", while the Hosted"
          + " Tokenization Page's own response example carries a real value in it,"
          + " \"preprod.direct.worldline-solutions.com//hostedtokenization/tokenization/form/"
          + "2f4d08d6ddfc411e944e81c54d824a99\". That example has two slashes after the host and"
          + " no protocol, and the vendor's instruction beside it is \"You will need to add the"
          + " protocol and the relevant subdomain to this URL, before redirecting your customer"
          + " to this URL\". This simulator reproduces the published shape, double slash and all,"
          + " under its own public base URL rather than the vendor's host, and does not tidy it:"
          + " the page itself is served at hostedTokenizationUrl";

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String NO_DECLINE_IS_PUBLISHED_FOR_THIS_ROUTE =
      "provider worldline declares no declined outcome for the hosted tokenization routes at all:"
          + " it publishes a create and a read and the general HTTP response codes, and no"
          + " decline table, no paymentErrorResponse and no errors.id of its own for either. A"
          + " rule that declines one of them is therefore answered with the bare errorResponse at"
          + " 400 carrying the code the rule named and the published errors.id"
          + " PAYMENT_PRODUCT_NOT_AVAILABLE, which is an inference and not a published answer";

  private final WorldlineTokenizations sessions;
  private final WorldlineAnswers answers;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final String publicBaseUrl;

  CreateHostedTokenization(
      WorldlineTokenizations sessions,
      WorldlineAnswers answers,
      IdGenerator ids,
      ScenarioEngine scenarios,
      String publicBaseUrl) {
    this.sessions = sessions;
    this.answers = answers;
    this.ids = ids;
    this.scenarios = scenarios;
    this.publicBaseUrl = publicBaseUrl;
  }

  WorldlineOperationResult handle(
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    if (call.carriesABody() && call.body().isEmpty()) {
      return answers.refusedValue(null, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    List<String> namedTokens = theTokensIn(call.body().orElseGet(WorldlineJson::empty));
    String hostedTokenizationId = WorldlineIdentifiers.aHostedTokenizationId(ids);
    return WorldlineOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, hostedTokenizationId)),
        () -> opened(testRunId, merchant, now, askedBy, hostedTokenizationId, namedTokens),
        code ->
            answers.declinedOnARouteThatPublishesNoDecline(
                code, NO_DECLINE_IS_PUBLISHED_FOR_THIS_ROUTE));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String hostedTokenizationId) {
    return new ScenarioRequest(
        WorldlineApi.WORLDLINE,
        testRunId,
        WorldlineStoryKey.ofHostedTokenization(hostedTokenizationId),
        merchant.accountId(),
        null,
        null,
        null,
        null,
        null,
        null,
        WorldlineOperations.CREATE_HOSTED_TOKENIZATION,
        headers);
  }

  private WorldlineOperationResult opened(
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      String firstId,
      List<String> namedTokens) {
    WorldlineTokenization session = aSessionClaimedFor(testRunId, merchant, now, askedBy, firstId);
    Map<String, WorldlineJson> answer = new LinkedHashMap<>();
    answer.put(
        WorldlineFields.HOSTED_TOKENIZATION_ID, WorldlineJson.text(session.hostedTokenizationId()));
    answer.put(
        WorldlineFields.HOSTED_TOKENIZATION_URL,
        WorldlineJson.text(hostedTokenizationUrl(session.hostedTokenizationId())));
    answer.put(
        WorldlineFields.PARTIAL_REDIRECT_URL,
        WorldlineJson.text(partialRedirectUrl(session.hostedTokenizationId())));
    List<WorldlineJson> unknown = new ArrayList<>();
    for (String named : namedTokens) {
      if (sessions.holdingTheToken(testRunId, named).isEmpty()) {
        unknown.add(WorldlineJson.text(named));
      }
    }
    if (!unknown.isEmpty()) {
      answer.put(WorldlineFields.INVALID_TOKENS, WorldlineJson.array(unknown));
    }
    return WorldlineOperationResult.succeeded(
        session.hostedTokenizationId(),
        WorldlineBody.of(WorldlineAnswers.OK, WorldlineJson.object(answer)));
  }

  private WorldlineTokenization aSessionClaimedFor(
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      String firstId) {
    String hostedTokenizationId = firstId;
    for (int attempt = 0; attempt < SESSION_ATTEMPTS; attempt++) {
      if (attempt > 0) {
        hostedTokenizationId = WorldlineIdentifiers.aHostedTokenizationId(ids);
      }
      WorldlineTokenization session =
          new WorldlineTokenization(
              testRunId,
              hostedTokenizationId,
              merchant.accountId(),
              merchant.merchantId(),
              WorldlineStatus.AWAITING_THE_PAYER,
              null,
              null,
              null,
              now.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN),
              askedBy,
              now,
              now);
      if (sessions.insertIfAbsent(session)) {
        return session;
      }
    }
    throw new IllegalStateException(
        "no Worldline hostedTokenizationId could be claimed in " + SESSION_ATTEMPTS + " attempts");
  }

  String hostedTokenizationUrl(String hostedTokenizationId) {
    return theHostedTokenizationUrlOf(publicBaseUrl, hostedTokenizationId);
  }

  String partialRedirectUrl(String hostedTokenizationId) {
    return thePartialRedirectUrlOf(publicBaseUrl, hostedTokenizationId);
  }

  static String theHostedTokenizationUrlOf(String publicBaseUrl, String hostedTokenizationId) {
    return publicBaseUrl + THE_PUBLISHED_FORM_PATH + hostedTokenizationId;
  }

  static String thePartialRedirectUrlOf(String publicBaseUrl, String hostedTokenizationId) {
    URI base = URI.create(publicBaseUrl);
    return base.getRawAuthority()
        + base.getRawPath()
        + "/"
        + THE_PUBLISHED_FORM_PATH
        + hostedTokenizationId;
  }

  static List<String> theTokensIn(WorldlineJson.JsonObject body) {
    List<String> named = new ArrayList<>();
    body.array(WorldlineFields.TOKENS)
        .ifPresent(
            listed -> {
              for (WorldlineJson element : listed.elements()) {
                if (element instanceof WorldlineJson.JsonText token && !token.value().isBlank()) {
                  named.add(token.value());
                }
              }
            });
    return List.copyOf(named);
  }
}
