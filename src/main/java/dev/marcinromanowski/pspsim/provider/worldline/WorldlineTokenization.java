package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record WorldlineTokenization(
    TestRunId testRunId,
    String hostedTokenizationId,
    String accountId,
    String merchantId,
    String nativeStatus,
    @Nullable String token,
    @Nullable String cardNumber,
    @Nullable String expiryDate,
    Instant expiresAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final Duration THE_PUBLISHED_LIFE_SPAN = Duration.ofHours(3);

  static final String THE_PUBLISHED_LIFE_SPAN_SENTENCE =
      "provider worldline publishes one lifetime for a hosted tokenization session and this"
          + " simulator enforces it: \"Sessions have a maximum life span of 3 hours. This means"
          + " that you can only retrieve this information while the session has not timed-out\"";

  static final String A_TOKEN_OF_THIS_SIMULATOR_IS_NEVER_TEMPORARY =
      "provider worldline publishes isTemporary as a required member of tokenResponse and"
          + " publishes its rule as a lifetime and a use count: \"Temporary tokens have a lifespan"
          + " of two hours and can only be used once\". It publishes nothing that says which"
          + " requests produce a temporary token, so every token this simulator mints is"
          + " permanent and answers isTemporary false. That is an inference and not a published"
          + " choice";

  String statusAt(Instant now) {
    return isAwaitingThePayer() && hasExpiredBy(now) ? WorldlineStatus.EXPIRED : nativeStatus;
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    return WorldlineStatus.normalizedTokenizationStatus(statusAt(now));
  }

  boolean isAwaitingThePayer() {
    return WorldlineStatus.AWAITING_THE_PAYER.equals(nativeStatus);
  }

  boolean isAwaitingThePayerAt(Instant now) {
    return WorldlineStatus.AWAITING_THE_PAYER.equals(statusAt(now));
  }

  boolean hasExpiredBy(Instant now) {
    return !now.isBefore(expiresAt);
  }

  boolean holdsAToken() {
    return token != null;
  }

  WorldlineTokenization tokenizedAs(
      String mintedToken, String maskedCardNumber, String cardExpiryDate, Instant at) {
    return new WorldlineTokenization(
        testRunId,
        hostedTokenizationId,
        accountId,
        merchantId,
        WorldlineStatus.TOKEN_CREATED,
        mintedToken,
        maskedCardNumber,
        cardExpiryDate,
        expiresAt,
        origin,
        createdAt,
        at);
  }

  WorldlineTokenization expiredAt(Instant at) {
    return new WorldlineTokenization(
        testRunId,
        hostedTokenizationId,
        accountId,
        merchantId,
        WorldlineStatus.EXPIRED,
        token,
        cardNumber,
        expiryDate,
        expiresAt,
        origin,
        createdAt,
        at);
  }

  String whyItIsNoLongerWaitingAt(Instant now) {
    String status = statusAt(now);
    if (WorldlineStatus.TOKEN_CREATED.equals(status)) {
      return "this hosted tokenization session has already tokenized a card, and provider"
          + " worldline publishes tokenStatus CREATED for exactly that: \"CREATED - The token has"
          + " been created\". The token it produced is on this object and is what a payment"
          + " charges";
    }
    if (WorldlineStatus.EXPIRED.equals(status)) {
      return "this hosted tokenization session is past the three hours provider worldline gives"
          + " one. "
          + THE_PUBLISHED_LIFE_SPAN_SENTENCE;
    }
    return "this hosted tokenization session holds "
        + status
        + ", which is not a state a payer can tokenize a card in. "
        + WorldlineStatus.NO_STATUS_IS_PUBLISHED_FOR_A_TOKENIZATION_SESSION;
  }
}
