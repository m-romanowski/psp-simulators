package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

record TranzilaPage(
    String pageId,
    TestRunId testRunId,
    String accountId,
    String terminalName,
    String sum,
    TranzilaPageCurrency currency,
    TranzilaTranmode tranmode,
    @Nullable String successUrl,
    @Nullable String failUrl,
    @Nullable String notifyUrl,
    @Nullable String dcDisable,
    @Nullable String thtk,
    @Nullable String requestParams,
    SequencedMap<String, String> echoed,
    ObjectOrigin origin,
    Instant createdAt,
    @Nullable PayerOutcome outcome,
    @Nullable Long transactionId,
    @Nullable String response,
    @Nullable String token,
    @Nullable String cardMask,
    @Nullable Integer cardType,
    @Nullable TranzilaThreeDSecureOutcome publishedAs,
    @Nullable Instant settledAt) {

  static final String ID_LEAD = "directng_";

  static final int ID_HEX = 24;

  TranzilaPage {
    echoed = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(echoed));
  }

  static TranzilaPage opened(
      String pageId,
      TranzilaTerminal terminal,
      TranzilaPageRequest request,
      @Nullable String requestParams,
      ObjectOrigin origin,
      Instant now) {
    return new TranzilaPage(
        pageId,
        terminal.testRunId(),
        terminal.accountId(),
        terminal.terminalName(),
        request.sum(),
        request.currency(),
        request.tranmode(),
        request.successUrl(),
        request.failUrl(),
        request.notifyUrl(),
        request.dcDisable(),
        request.thtk(),
        requestParams,
        request.echoed(),
        origin,
        now,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  boolean isSettled() {
    return outcome != null;
  }

  TranzilaPage settledAs(
      PayerOutcome reached,
      @Nullable Long transaction,
      @Nullable String answered,
      @Nullable String saved,
      @Nullable TranzilaCard card,
      Instant at) {
    return new TranzilaPage(
        pageId,
        testRunId,
        accountId,
        terminalName,
        sum,
        currency,
        tranmode,
        successUrl,
        failUrl,
        notifyUrl,
        dcDisable,
        thtk,
        requestParams,
        echoed,
        origin,
        createdAt,
        reached,
        transaction,
        answered,
        saved,
        card == null ? null : card.cardMask(),
        card == null ? null : Integer.valueOf(card.cardType()),
        card == null ? null : card.publishedAs(),
        at);
  }

  String theOperation() {
    return TranzilaFields.TRANMODE + " " + tranmode.wire();
  }
}
