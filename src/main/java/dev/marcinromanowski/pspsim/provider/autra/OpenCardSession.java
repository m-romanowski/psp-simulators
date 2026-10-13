package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class OpenCardSession {

  static final int SESSION_ATTEMPTS = 5;

  static final int PAGE_TOKEN_HEX_DIGITS = 24;

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant this hosted card session belongs to, not the"
          + " cardholder";

  static final String NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT =
      "no merchant of this tenant answers to the documentId this call carries";

  private final AutraCardSessions sessions;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final String publicBaseUrl;

  OpenCardSession(
      AutraCardSessions sessions, IdGenerator ids, ScenarioEngine scenarios, String publicBaseUrl) {
    this.sessions = sessions;
    this.ids = ids;
    this.scenarios = scenarios;
    this.publicBaseUrl = publicBaseUrl;
  }

  AutraOperationResult handle(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    Optional<AutraJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    AutraJson.JsonObject body = parsed.get();
    Optional<String> documentId = PreAuthorize.textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    String cardSessionId = AutraIdentifiers.uuidOf(ids);
    return AutraOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, cardSessionId)),
        () -> opened(body, testRunId, merchant, now, askedBy, cardSessionId),
        code -> declined(merchant, code));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      String cardSessionId) {
    return new ScenarioRequest(
        AutraApi.AUTRA,
        testRunId,
        AutraStoryKey.ofCardSession(cardSessionId),
        merchant.accountId(),
        null,
        null,
        null,
        null,
        null,
        null,
        AutraOperations.CARD_SESSION,
        headers);
  }

  private AutraOperationResult opened(
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      String cardSessionId) {
    AutraCardSession session =
        aSessionClaimedFor(body, testRunId, merchant, now, askedBy, cardSessionId);
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.HOSTED_CARD_SESSION_ID, AutraJson.text(session.cardSessionId()));
    answer.put(
        AutraFields.HOSTED_CARD_URL,
        AutraJson.text(
            AutraCardPage.urlFor(publicBaseUrl, session.cardSessionId(), session.pageToken())));
    answer.put(
        AutraFields.EXPIRES_AT,
        AutraJson.text(CreatePixCheckout.asAutraWritesIt(session.expiresAt())));
    return AutraOperationResult.succeeded(
        session.cardSessionId(), AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
  }

  private AutraCardSession aSessionClaimedFor(
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      String firstId) {
    String cardSessionId = firstId;
    for (int attempt = 0; attempt < SESSION_ATTEMPTS; attempt++) {
      String pageToken = ids.hex(PAGE_TOKEN_HEX_DIGITS).toLowerCase(Locale.ROOT);
      AutraCardSession session =
          new AutraCardSession(
              testRunId,
              cardSessionId,
              merchant.accountId(),
              merchant.documentId(),
              PreAuthorize.textIn(body, AutraFields.NICKNAME).orElse(null),
              AutraStatus.PENDING,
              pageToken,
              null,
              now.plus(AutraCardSession.THE_PUBLISHED_TTL),
              askedBy,
              now,
              now);
      if (sessions.insertIfAbsent(session)) {
        return session;
      }
      cardSessionId = AutraIdentifiers.uuidOf(ids);
    }
    throw new IllegalStateException(
        "no Autra hostedCardSessionId could be claimed in " + SESSION_ATTEMPTS + " attempts");
  }

  private static AutraOperationResult declined(AutraMerchant merchant, String code) {
    return AutraOperationResult.failed(
        AutraRefusals.theBodyWasRefused(),
        AutraBody.of(
            AutraAnswers.UNPROCESSABLE,
            AutraErrors.envelope(code, AutraDeclineMessages.of(code, merchant))));
  }
}
