package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OpenAcquiringSession {

  static final int SESSION_ATTEMPTS = 5;

  static final int SESSION_ENTROPY_BYTES = 20;

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant this session is opened for";

  static final String NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT =
      "no merchant of this tenant answers to the documentId this call carries";

  static final String THE_ONLY_CODE_AUTRA_PUBLISHES = AutraStatus.ACCEPTED;

  private final AutraSessions sessions;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  OpenAcquiringSession(AutraSessions sessions, IdGenerator ids, ScenarioEngine scenarios) {
    this.sessions = sessions;
    this.ids = ids;
    this.scenarios = scenarios;
  }

  AutraOperationResult handle(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      AutraSessionKind kind) {
    Optional<AutraJson.JsonObject> body = call.body();
    if (body.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    Optional<String> documentId = body.get().text(AutraFields.DOCUMENT_ID);
    if (documentId.filter(sent -> !sent.isBlank()).isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    return AutraOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, kind)),
        () -> opened(testRunId, merchant, now, kind),
        code -> declined(merchant, code));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraSessionKind kind) {
    return new ScenarioRequest(
        AutraApi.AUTRA,
        testRunId,
        kind.storyKeyOf(merchant.documentId()),
        merchant.accountId(),
        null,
        null,
        null,
        null,
        null,
        null,
        kind.operation(),
        headers);
  }

  private AutraOperationResult opened(
      TestRunId testRunId, AutraMerchant merchant, Instant now, AutraSessionKind kind) {
    AutraSession session = aSessionClaimedFor(testRunId, merchant, now);
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    if (kind.answersWithACode()) {
      answer.put(AutraFields.CODE, AutraJson.text(THE_ONLY_CODE_AUTRA_PUBLISHES));
    }
    answer.put(AutraFields.TRANSACTIONAL_TOKEN, AutraJson.text(session.transactionalToken()));
    return AutraOperationResult.succeeded(
        null, AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
  }

  private static AutraOperationResult declined(AutraMerchant merchant, String code) {
    return AutraOperationResult.failed(
        AutraRefusals.theBodyWasRefused(),
        AutraBody.of(
            AutraAnswers.UNPROCESSABLE,
            AutraErrors.envelope(code, AutraDeclineMessages.of(code, merchant))));
  }

  AutraSession aSessionClaimedFor(TestRunId testRunId, AutraMerchant merchant, Instant now) {
    for (int attempt = 0; attempt < SESSION_ATTEMPTS; attempt++) {
      AutraSession session =
          new AutraSession(
              testRunId,
              ids.hex(SESSION_ENTROPY_BYTES),
              merchant.accountId(),
              merchant.documentId(),
              now);
      if (sessions.open(session)) {
        return session;
      }
    }
    throw new IllegalStateException(
        "no Autra transactionalToken could be claimed in " + SESSION_ATTEMPTS + " attempts");
  }
}
