package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TranzilaLookups {

  static final String BAD_REQUEST = "Bad Request";

  static final String UNAUTHORIZED = "Unauthorized";

  static final String ERROR = "error";

  static final String INVALID_ACCESS_TOKEN =
      "Invalid access token. Verify your HMAC signature and that your timestamp is current.";

  static final String NOT_A_JSON_OBJECT = "The request body is not a JSON object.";

  static final String ANOTHER_TERMINAL =
      "terminal_name names a terminal other than the one the X-tranzila-api-app-key belongs to";

  private final TranzilaRecords records;

  TranzilaLookups(TranzilaRecords records) {
    this.records = records;
  }

  TranzilaOperationResult ofTheApi(TranzilaApiCall call, TranzilaTerminal terminal) {
    Optional<TranzilaJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return TranzilaAnswers.refusedBody(
          TranzilaErrors.INVALID_JSON_FORMAT,
          TranzilaErrors.INVALID_JSON_FORMAT_TEXT,
          CreateTransaction.NOT_A_JSON_OBJECT);
    }
    TranzilaLookup lookup;
    try {
      lookup = TranzilaLookup.ofTheApi(parsed.get());
    } catch (TranzilaSchemaMismatch notTheSchema) {
      return TranzilaAnswers.refusedSchema(notTheSchema.why());
    }
    if (!lookup.terminalName().equals(terminal.terminalName())) {
      return TranzilaAnswers.refusedCredential(
          TranzilaErrors.AUTHORIZATION_FAILED,
          TranzilaErrors.AUTHORIZATION_FAILED_TEXT,
          ANOTHER_TERMINAL);
    }
    return TranzilaOperationResult.succeeded(
        null,
        TranzilaBody.of(
            TranzilaAnswers.OK, TranzilaLookupAnswers.ofTheApi(found(terminal, lookup))));
  }

  TranzilaOperationResult ofTheReport(TranzilaApiCall call, TranzilaTerminal terminal) {
    Optional<TranzilaJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return badRequest(NOT_A_JSON_OBJECT);
    }
    TranzilaLookup lookup;
    try {
      lookup = TranzilaLookup.ofTheReport(parsed.get());
    } catch (TranzilaSchemaMismatch notTheSchema) {
      return badRequest(notTheSchema.why());
    }
    if (!lookup.terminalName().equals(terminal.terminalName())) {
      return unauthorized(ANOTHER_TERMINAL);
    }
    return TranzilaOperationResult.succeeded(
        null,
        TranzilaBody.of(
            TranzilaAnswers.OK, TranzilaLookupAnswers.ofTheReport(found(terminal, lookup))));
  }

  TranzilaLookupAnswers.Found found(TranzilaTerminal terminal, TranzilaLookup lookup) {
    TestRunId testRunId = terminal.testRunId();
    String accountId = terminal.accountId();
    Long index = lookup.index();
    List<TranzilaRecorded> rows;
    long total;
    if (index != null) {
      List<TranzilaRecorded> named =
          records.byIndex(testRunId, accountId, index.longValue()).stream().toList();
      rows = lookup.thePageOf(named);
      total = named.size();
    } else {
      TranzilaLookup.Window window = lookup.window();
      rows = records.between(testRunId, accountId, window);
      total = records.countedBetween(testRunId, accountId, window.start(), window.end());
    }
    return new TranzilaLookupAnswers.Found(
        terminal.terminalName(),
        rows,
        total,
        records.slotsOf(testRunId, accountId),
        lookup.detailed());
  }

  static TranzilaOperationResult badRequest(String why) {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theBodyWasRefused(),
        TranzilaBody.of(TranzilaAnswers.BAD_REQUEST, theReportsError(BAD_REQUEST, why)));
  }

  static TranzilaOperationResult unauthorized(String why) {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theCredentialWasRefused(),
        TranzilaBody.of(
            TranzilaAnswers.UNAUTHORIZED,
            theReportsError(UNAUTHORIZED, INVALID_ACCESS_TOKEN + " " + why + ".")));
  }

  static TranzilaJson.JsonObject theReportsError(String error, String message) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(ERROR, TranzilaJson.text(error));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(message));
    return TranzilaJson.object(body);
  }
}
