package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

final class AutraFlow {

  static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static int sessionsOpened;

  private AutraFlow() {}

  static String bearer(AutraApi api, String accountId) {
    return AutraTokenTest.theTokenIn(
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS,
                AutraWire.basic("client-" + accountId, "secret-" + accountId))));
  }

  static synchronized String session(
      AutraDoubles.Sessions sessions, TestRunId runId, String accountId, String documentId) {
    sessionsOpened++;
    String token = "session-token-" + sessionsOpened;
    sessions.open(new AutraSession(runId, token, accountId, documentId, WHEN));
    return token;
  }

  static AutraApiResponse sell(
      AutraApi api,
      TestRunId runId,
      String accountId,
      String documentId,
      AutraDoubles.Sessions sessions,
      String amount,
      @Nullable String orderId) {
    String body =
        "{\"transactionalToken\":\""
            + session(sessions, runId, accountId, documentId)
            + "\",\"documentId\":\""
            + documentId
            + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":"
            + amount
            + ",\"installments\":1"
            + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
            + ",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}}";
    return api.sale(
        AutraWire.call(
            AutraApiController.PAYMENTS, AutraWire.bearer(bearer(api, accountId)), body));
  }

  static AutraApiResponse reserve(
      AutraApi api,
      TestRunId runId,
      String accountId,
      String documentId,
      AutraDoubles.Sessions sessions,
      String amount,
      @Nullable String orderId) {
    String body =
        "{\"transactionalToken\":\""
            + session(sessions, runId, accountId, documentId)
            + "\",\"documentId\":\""
            + documentId
            + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":"
            + amount
            + ",\"installments\":1"
            + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
            + ",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}}";
    return api.preAuthorization(
        AutraWire.call(
            AutraApiController.PRE_AUTHORIZATION, AutraWire.bearer(bearer(api, accountId)), body));
  }
}
