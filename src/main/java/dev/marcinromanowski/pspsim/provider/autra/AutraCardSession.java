package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record AutraCardSession(
    TestRunId testRunId,
    String cardSessionId,
    String accountId,
    String documentId,
    @Nullable String nickname,
    String nativeStatus,
    String pageToken,
    @Nullable AutraStoredCard storedCard,
    Instant expiresAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final Duration THE_PUBLISHED_TTL = Duration.ofMinutes(15);

  static final String TOKENIZED = "TOKENIZED";

  static final String NO_STATUS_IS_PUBLISHED_FOR_A_SESSION =
      "provider autra publishes no status vocabulary for a hosted card session at all: the create"
          + " answers hostedCardSessionId, hostedCardUrl and expiresAt and no status field, and"
          + " the only lifecycle it publishes is \"Curta duracao e uso unico\" and \"After"
          + " expiresAt the session no longer loads — create a new one\". The three values this"
          + " simulator carries are therefore its own: PENDING and EXPIRED are borrowed from the"
          + " payment status list the vendor does publish, and "
          + TOKENIZED
          + " is taken from the words the vendor answers a tokenization with, \"Cartao"
          + " tokenizado\" and AutraCardTokenizedResult";

  String statusAt(Instant now) {
    return isAwaitingThePayer() && hasExpiredBy(now) ? AutraStatus.EXPIRED : nativeStatus;
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    return switch (statusAt(now)) {
      case AutraStatus.PENDING -> NormalizedStatus.PROCESSING;
      case TOKENIZED -> NormalizedStatus.SUCCEEDED;
      case AutraStatus.EXPIRED -> NormalizedStatus.CANCELLED;
      default -> NormalizedStatus.UNKNOWN;
    };
  }

  boolean isAwaitingThePayer() {
    return AutraStatus.PENDING.equals(nativeStatus);
  }

  boolean isAwaitingThePayerAt(Instant now) {
    return AutraStatus.PENDING.equals(statusAt(now));
  }

  boolean hasExpiredBy(Instant now) {
    return !now.isBefore(expiresAt);
  }

  AutraCardSession tokenizedAs(AutraStoredCard card, Instant at) {
    return new AutraCardSession(
        testRunId,
        cardSessionId,
        accountId,
        documentId,
        nickname,
        TOKENIZED,
        pageToken,
        card,
        expiresAt,
        origin,
        createdAt,
        at);
  }

  AutraCardSession expiredAt(Instant at) {
    return new AutraCardSession(
        testRunId,
        cardSessionId,
        accountId,
        documentId,
        nickname,
        AutraStatus.EXPIRED,
        pageToken,
        storedCard,
        expiresAt,
        origin,
        createdAt,
        at);
  }

  String whyItIsNoLongerWaitingAt(Instant now) {
    String status = statusAt(now);
    if (TOKENIZED.equals(status)) {
      return "this hosted card session has already tokenized a card, and provider autra publishes"
          + " its page as \"Curta duracao e uso unico\" and instructs \"mint one per attempt\","
          + " so a payer cannot open it a second time";
    }
    if (AutraStatus.EXPIRED.equals(status)) {
      return "this hosted card session is past the fifteen minutes provider autra gives one — \"E"
          + " a criacao mais 15 minutos (TTL)\" — and it publishes that \"After expiresAt the"
          + " session no longer loads — create a new one\"";
    }
    return "this hosted card session holds "
        + status
        + ", which is not a state a payer can tokenize a card in. "
        + NO_STATUS_IS_PUBLISHED_FOR_A_SESSION;
  }
}
