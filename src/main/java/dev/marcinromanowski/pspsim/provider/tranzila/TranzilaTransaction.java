package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record TranzilaTransaction(
    TestRunId testRunId,
    long transactionId,
    long rootTxnId,
    String accountId,
    String terminalName,
    TranzilaTxnType type,
    @Nullable Integer verifyMode,
    @Nullable Long referenceTxnId,
    String processorResponseCode,
    String amount,
    String currencyCode,
    @Nullable String authNumber,
    @Nullable String token,
    TranzilaCard card,
    String responseCvv,
    String responseId,
    @Nullable String dcDisable,
    @Nullable Instant holdExpiresAt,
    ObjectOrigin origin,
    Instant createdAt,
    @Nullable TranzilaWallet broker) {

  boolean isTheOriginal() {
    return transactionId == rootTxnId;
  }

  boolean approved() {
    return TranzilaResponseCodes.approves(processorResponseCode);
  }

  boolean isAJ5() {
    return type == TranzilaTxnType.VERIFY && Integer.valueOf(TranzilaTxnType.J5).equals(verifyMode);
  }

  boolean isAJ2() {
    return type == TranzilaTxnType.VERIFY && Integer.valueOf(TranzilaTxnType.J2).equals(verifyMode);
  }

  boolean references(long transactionId) {
    Long referenced = referenceTxnId;
    return referenced != null && referenced.longValue() == transactionId;
  }

  BigDecimal theAmount() {
    return new BigDecimal(amount);
  }

  Optional<String> tranmode() {
    return type.tranmodeOf(verifyMode);
  }

  String transactionIdAsText() {
    return Long.toString(transactionId);
  }

  String nativeStatus() {
    Integer mode = verifyMode;
    return mode == null
        ? type.wire() + " " + processorResponseCode
        : type.wire() + " " + mode + " " + processorResponseCode;
  }

  boolean isLiveAt(Instant now) {
    Instant expires = holdExpiresAt;
    return expires != null && now.isBefore(expires);
  }
}
