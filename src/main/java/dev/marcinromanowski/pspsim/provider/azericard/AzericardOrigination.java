package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;

final class AzericardOrigination {

  static final String A_HOLD = "TRTYPE=0";

  static final String A_CHARGE = "TRTYPE=1";

  private static final ThePayerStep THE_PAYMENT_PAGE =
      new ThePayerStep.APageThisSimulatorServes(AzericardPayerPage.PATH);

  private static final String IT_IS_LISTED_ALREADY =
      " The payment exists the moment this screen answers: it is on the Payments screen now, under"
          + " the INT_REF above, and the actions offered there act on it.";

  private static final AFieldATesterFillsIn ORDER =
      AFieldATesterFillsIn.typed(
          AzericardFields.ORDER,
          "the merchant's order number",
          true,
          "Azericard requires a numeric order of "
              + AzericardOrderTrace.SHORTEST_ORDER
              + " to "
              + AzericardOrderTrace.LONGEST_ORDER
              + " digits, and reads its last "
              + AzericardOrderTrace.TRACE_LENGTH
              + " digits as the trace number that must be unique for this terminal within one day."
              + " A payment whose last six digits repeat one this terminal already used today is"
              + " answered as a duplicate and creates nothing.",
          "20240115103000");

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed(
          AzericardFields.AMOUNT,
          "amount",
          true,
          "The amount azericard puts on the card, written the way azericard writes money.",
          "11.48");

  private static final AFieldATesterFillsIn CURRENCY =
      AFieldATesterFillsIn.typed(
          AzericardFields.CURRENCY,
          "currency",
          true,
          "Azericard publishes this as three symbols and writes AZN in every request example it"
              + " publishes. It publishes no list of the codes it accepts, so this simulator"
              + " refuses none of them.",
          "AZN");

  private static final AFieldATesterFillsIn DESC =
      AFieldATesterFillsIn.typed(
          AzericardFields.DESC,
          "what the payment is for",
          false,
          "Azericard carries a free description on the request and returns it on the answer. It is"
              + " not part of the signed material and nothing in this simulator matches on it.",
          "one seat, row F");

  private static final AFieldATesterFillsIn EMAIL =
      AFieldATesterFillsIn.typed(
          AzericardFields.EMAIL,
          "the payer's email address",
          false,
          "Azericard carries an email address on the request and requires none. It is a payer's"
              + " personal data: this simulator stores it as it was typed and does not redact it"
              + " in the audit trail.",
          "dana@example.com");

  private static final AFieldATesterFillsIn BACKREF =
      AFieldATesterFillsIn.theCallbackTarget(
          AzericardFields.BACKREF,
          "where azericard posts the callback",
          "Azericard names no callback event of its own: BACKREF is the address it posts the"
              + " outcome of this payment to. It refuses to post one to an address this test run"
              + " does not configure as a callback target, so pick one of the addresses this run"
              + " configures, or leave it empty and azericard uses the single target configured"
              + " for this terminal.");

  private static final List<AFieldATesterFillsIn> THE_FIELDS_AN_AUTHORIZATION_CARRIES =
      List.of(ORDER, AMOUNT, CURRENCY, DESC, EMAIL, BACKREF);

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          AzericardGateway.AZERICARD,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Hold the funds on the card and complete the sale later",
                  "An authorization that holds the funds and moves none of them yet. Complete it"
                      + " for the whole amount or part of it, or reverse it, from the Payments"
                      + " screen; what a partial completion leaves of the hold is the terminal's"
                      + " own "
                      + AzericardPartialCompletion.SETTING
                      + " setting."
                      + IT_IS_LISTED_ALREADY,
                  THE_PAYMENT_PAGE,
                  THE_FIELDS_AN_AUTHORIZATION_CARRIES),
              new DeclaredOrigination(
                  A_CHARGE,
                  "Take the funds in one call, with nothing left to complete",
                  "An authorization that takes the funds in the same call. There is no hold left"
                      + " to complete: what the Payments screen offers on it afterwards is a"
                      + " reversal that returns the funds."
                      + IT_IS_LISTED_ALREADY,
                  THE_PAYMENT_PAGE,
                  THE_FIELDS_AN_AUTHORIZATION_CARRIES)),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        AzericardFields.TERMINAL,
        "azericard names the merchant by the TERMINAL configured for the account this payment is"
            + " started on, and that terminal is the account.");
    filled.put(
        AzericardFields.TRTYPE,
        "azericard decides on TRTYPE whether a request holds the funds or takes them, and the way"
            + " of starting the payment you picked above is that choice.");
    filled.put(
        AzericardFields.TIMESTAMP,
        "azericard requires a merchant timestamp and refuses a request whose timestamp is outside"
            + " the window around its own clock. The simulator writes the clock of this test run"
            + " into it, so a pinned clock stays pinned.");
    filled.put(
        AzericardFields.NONCE,
        "azericard requires a fresh nonce on every request. The simulator draws one.");
    filled.put(
        AzericardFields.P_SIGN,
        "azericard requires every request on its wire to carry a MAC made with the merchant's"
            + " private key. This test run configures the merchant's public key, which verifies"
            + " such a MAC and cannot make one, so a payment started from this screen is not"
            + " signed as the merchant and does not travel that wire: the simulator places it on"
            + " the terminal you chose, and the audit trail records it as a UI action rather than"
            + " as a call that arrived from a client.");
    return filled;
  }

  private AzericardOrigination() {}

  static AzericardMessage messageOf(
      OriginationRequest request, AzericardTerminal terminal, IdGenerator ids, Instant now) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(AzericardFields.TERMINAL, List.of(terminal.terminalId()));
    fields.put(AzericardFields.TRTYPE, List.of(theTypeThatCarries(request).wireValue()));
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      fields.put(value.getKey(), List.of(value.getValue()));
    }
    fields.put(AzericardFields.TIMESTAMP, List.of(AzericardTimestamp.write(now)));
    fields.put(
        AzericardFields.NONCE,
        List.of(ids.hex(AzericardCallbacks.NONCE_LENGTH).toUpperCase(Locale.ROOT)));
    return AzericardMessage.of(AzericardRoute.CGI_LINK, fields);
  }

  private static AzericardTransactionType theTypeThatCarries(OriginationRequest request) {
    return A_CHARGE.equals(request.startedAs())
        ? AzericardTransactionType.AUTHORIZATION
        : AzericardTransactionType.PRE_AUTHORIZATION;
  }

  static OriginationOutcome outcomeOf(AzericardOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }

  private static String theWordsOf(AzericardOperationResult result) {
    Map<String, List<String>> answered = result.body().fields();
    return "provider azericard answered "
        + AzericardFields.ACTION
        + "="
        + firstOf(answered, AzericardResponse.ACTION_ELEMENT)
        + " "
        + AzericardFields.RC
        + "="
        + firstOf(answered, AzericardResponse.RC_ELEMENT)
        + " "
        + AzericardFields.DESC
        + "="
        + firstOf(answered, AzericardResponse.DESC_ELEMENT);
  }

  private static String firstOf(Map<String, List<String>> answered, String element) {
    return answered.getOrDefault(element, List.of()).stream().findFirst().orElse("");
  }
}
