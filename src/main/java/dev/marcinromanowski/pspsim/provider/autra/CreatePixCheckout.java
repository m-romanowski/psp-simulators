package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CreatePixCheckout {

  static final int FORBIDDEN = 403;

  static final int PIX_ID_ATTEMPTS = 5;

  static final int SLUG_HEX_DIGITS = 16;

  static final Duration THE_QR_LIVES_FOR = Duration.ofMinutes(5);

  static final String THE_PUBLISHED_QR_PREFIX = "00020126580014BR.GOV.BCB.PIX01";

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant this Pix is collected for, not the payer";

  static final String NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT =
      "no merchant of this tenant answers to the documentId this call carries";

  static final String THE_SESSION_TOKEN_IS_REQUIRED =
      "transactionalToken is required and names the Pix session this call spends";

  static final String THE_SESSION_TOKEN_NAMES_NO_SESSION =
      "transactionalToken names no acquiring session this tenant opened, which is the answer"
          + " provider autra publishes for a token that expired between the initialize and the"
          + " checkout: open a new session at POST /v1/acquiring/pix/initialize and send the token"
          + " it answers with";

  static final String THE_CURRENCY_IS_REQUIRED = "currency is required";

  static final String THE_AMOUNT_MUST_BE_ABOVE_ZERO =
      "amount is required and must be a number above zero";

  private static final DateTimeFormatter AS_AUTRA_WRITES_A_MOMENT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final AutraPixPayments pixPayments;
  private final AutraSessions sessions;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  CreatePixCheckout(
      AutraPixPayments pixPayments,
      AutraSessions sessions,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.pixPayments = pixPayments;
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
      ObjectOrigin askedBy) {
    Optional<AutraJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    AutraJson.JsonObject body = parsed.get();
    AutraOperationResult refused = theRefusalOf(body, testRunId, merchant);
    if (refused != null) {
      return refused;
    }
    return decided(body, headers, testRunId, merchant, now, askedBy);
  }

  private @Nullable AutraOperationResult theRefusalOf(
      AutraJson.JsonObject body, TestRunId testRunId, AutraMerchant merchant) {
    Optional<String> documentId = PreAuthorize.textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    Optional<String> transactionalToken =
        PreAuthorize.textIn(body, AutraFields.TRANSACTIONAL_TOKEN);
    if (transactionalToken.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_TRANSACTIONAL_TOKEN, THE_SESSION_TOKEN_IS_REQUIRED);
    }
    if (sessions.session(testRunId, transactionalToken.get()).isEmpty()) {
      return theSessionExpiredBetweenTheSteps();
    }
    if (PreAuthorize.textIn(body, AutraFields.CURRENCY).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, THE_CURRENCY_IS_REQUIRED);
    }
    Optional<String> amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT);
    if (amount.filter(AutraAmounts::isAboveZero).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    }
    return null;
  }

  static AutraOperationResult theSessionExpiredBetweenTheSteps() {
    return AutraOperationResult.failed(
        AutraRefusals.theCredentialWasRefused(),
        AutraBody.of(
            FORBIDDEN,
            AutraErrors.envelope(AutraErrors.UNAUTHORIZED, THE_SESSION_TOKEN_NAMES_NO_SESSION)));
  }

  private AutraOperationResult decided(
      AutraJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    String pixId = aPixIdNothingOfThisRunHolds(testRunId);
    return AutraOperationResult.decidedBy(
        scenarios.decide(requestOf(body, headers, testRunId, merchant, pixId)),
        () -> created(body, testRunId, merchant, now, askedBy, pixId),
        code -> declined(merchant, code));
  }

  private static ScenarioRequest requestOf(
      AutraJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      String pixId) {
    return new ScenarioRequest(
        AutraApi.AUTRA,
        testRunId,
        AutraStoryKey.ofPix(pixId),
        merchant.accountId(),
        PreAuthorize.theOrderIn(body),
        null,
        null,
        null,
        Amount.of(AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElse("")),
        PreAuthorize.textIn(body, AutraFields.CURRENCY).orElse(null),
        AutraOperations.PIX_CHECKOUT,
        headers);
  }

  private AutraOperationResult created(
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      String pixId) {
    String amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElseThrow();
    String currency = PreAuthorize.textIn(body, AutraFields.CURRENCY).orElseThrow();
    Instant expiresAt = now.plus(THE_QR_LIVES_FOR);
    AutraPixPayment pix =
        new AutraPixPayment(
            testRunId,
            pixId,
            merchant.accountId(),
            merchant.documentId(),
            PreAuthorize.theOrderIn(body),
            PreAuthorize.textIn(body, AutraFields.DESCRIPTION).orElse(null),
            Amount.of(amount),
            currency,
            AutraStatus.PENDING,
            theQrCodeOf(pixId),
            ids.hex(SLUG_HEX_DIGITS).toUpperCase(Locale.ROOT),
            ids.hex(SLUG_HEX_DIGITS).toUpperCase(Locale.ROOT),
            expiresAt,
            askedBy,
            now,
            now);
    if (!pixPayments.insertIfAbsent(pix)) {
      throw new IllegalStateException(
          "the Autra pixId this checkout was decided under was claimed by another request before"
              + " the Pix could be stored");
    }
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PIX_ID, AutraJson.text(pixId));
    answer.put(AutraFields.STATUS, AutraJson.text(AutraStatus.PENDING));
    answer.put(AutraFields.QR_CODE, AutraJson.text(pix.qrCode()));
    answer.put(AutraFields.AMOUNT, AutraJson.number(amount));
    answer.put(AutraFields.CURRENCY, AutraJson.text(currency));
    answer.put(AutraFields.EXPIRES_AT, AutraJson.text(asAutraWritesIt(expiresAt)));
    return AutraOperationResult.succeeded(
        pixId, AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
  }

  private static AutraOperationResult declined(AutraMerchant merchant, String code) {
    return AutraOperationResult.failed(
        RefusalSubject.anObjectItWillNotName(AutraRefusals.A_DECLINED_PIX_IS_NAMED_BY_NOTHING),
        AutraBody.of(
            AutraAnswers.UNPROCESSABLE,
            AutraErrors.envelope(code, AutraDeclineMessages.of(code, merchant))));
  }

  static String theQrCodeOf(String pixId) {
    return THE_PUBLISHED_QR_PREFIX + pixId;
  }

  static String asAutraWritesIt(Instant at) {
    return AS_AUTRA_WRITES_A_MOMENT.format(at);
  }

  private String aPixIdNothingOfThisRunHolds(TestRunId testRunId) {
    for (int attempt = 0; attempt < PIX_ID_ATTEMPTS; attempt++) {
      String pixId = ids.hex(AutraIdentifiers.UUID_HEX_DIGITS).toLowerCase(Locale.ROOT);
      if (pixPayments.findById(testRunId, pixId).isEmpty()) {
        return pixId;
      }
    }
    throw new IllegalStateException(
        "no Autra pixId could be drawn in " + PIX_ID_ATTEMPTS + " attempts");
  }
}
