package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CreateHandshake {

  static final String SUCCESS = "Success";

  static final String UNAUTHORIZED = "Unauthorized";

  static final String NO_MESSAGE = "";

  static final int THTK_ATTEMPTS = 5;

  static final Set<String> THE_FIELDS_THE_SCHEMA_PUBLISHES =
      Set.of(TranzilaFields.TERMINAL_NAME, TranzilaFields.SUM, TranzilaFields.REQUEST_PARAMS);

  private final TranzilaHandshakes handshakes;
  private final IdGenerator ids;

  CreateHandshake(TranzilaHandshakes handshakes, IdGenerator ids) {
    this.handshakes = handshakes;
    this.ids = ids;
  }

  TranzilaOperationResult handle(
      TranzilaApiCall call, TestRunId testRunId, TranzilaTerminal terminal, Instant now) {
    Optional<TranzilaJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty() || !aBodyTheSchemaAdmits(parsed.get())) {
      return refusedBody();
    }
    TranzilaJson.JsonObject body = parsed.get();
    if (!body.text(TranzilaFields.TERMINAL_NAME)
        .filter(terminal.terminalName()::equals)
        .isPresent()) {
      return refusedCredential();
    }
    String sum = new BigDecimal(body.number(TranzilaFields.SUM).orElseThrow()).toPlainString();
    String requestParams =
        body.object(TranzilaFields.REQUEST_PARAMS).map(TranzilaJsonWriter::toText).orElse(null);
    for (int attempt = 0; attempt < THTK_ATTEMPTS; attempt++) {
      TranzilaHandshake handshake =
          new TranzilaHandshake(
              TranzilaHandshake.THE_LEAD + ids.hex(TranzilaHandshake.HEX_DIGITS),
              testRunId,
              terminal.accountId(),
              terminal.terminalName(),
              sum,
              requestParams,
              now,
              now.plus(TranzilaHandshake.IT_IS_RETAINED_FOR));
      if (handshakes.insert(handshake) == 1) {
        return created(handshake);
      }
    }
    throw new IllegalStateException(
        "no Tranzila thtk could be claimed in " + THTK_ATTEMPTS + " attempts");
  }

  static boolean aBodyTheSchemaAdmits(TranzilaJson.JsonObject body) {
    if (!THE_FIELDS_THE_SCHEMA_PUBLISHES.containsAll(body.members().keySet())) {
      return false;
    }
    if (body.text(TranzilaFields.TERMINAL_NAME).isEmpty()) {
      return false;
    }
    Optional<String> sum = body.number(TranzilaFields.SUM);
    if (sum.isEmpty() || new BigDecimal(sum.get()).signum() <= 0) {
      return false;
    }
    return !body.has(TranzilaFields.REQUEST_PARAMS)
        || body.object(TranzilaFields.REQUEST_PARAMS).isPresent();
  }

  static TranzilaOperationResult created(TranzilaHandshake handshake) {
    Map<String, TranzilaJson> answered = new LinkedHashMap<>();
    answered.put(TranzilaFields.ERROR_CODE, TranzilaJson.number("0"));
    answered.put(TranzilaFields.MESSAGE, TranzilaJson.text(SUCCESS));
    answered.put(TranzilaFields.THTK, TranzilaJson.text(handshake.thtk()));
    return TranzilaOperationResult.succeeded(
        null, TranzilaBody.of(TranzilaAnswers.OK, TranzilaJson.object(answered)));
  }

  static TranzilaOperationResult refusedBody() {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theBodyWasRefused(),
        theHandshakeError(TranzilaAnswers.BAD_REQUEST, NO_MESSAGE));
  }

  static TranzilaOperationResult refusedCredential() {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theCredentialWasRefused(),
        theHandshakeError(TranzilaAnswers.UNAUTHORIZED, UNAUTHORIZED));
  }

  static TranzilaBody theHandshakeError(int httpStatus, String message) {
    Map<String, TranzilaJson> answered = new LinkedHashMap<>();
    answered.put(TranzilaFields.ERROR_CODE, TranzilaJson.number(Integer.toString(httpStatus)));
    answered.put(TranzilaFields.MESSAGE, TranzilaJson.text(message));
    return TranzilaBody.of(httpStatus, TranzilaJson.object(answered));
  }
}
