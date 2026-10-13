package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class GrowHostedCheckout {

  static final int OFFERED = 200;

  static final int REDIRECTED = 303;

  static final int REFUSED = 422;

  static final int NO_SUCH_LINK = 404;

  static final String POST = "POST";

  private static final int AUDIT_ID_LENGTH = 24;

  record Rendered(int httpStatus, String contentType, @Nullable String location, String body) {

    Map<String, String> headers() {
      return location == null
          ? GrowHostedPage.DEFENCE_HEADERS
          : GrowHostedPage.NOTHING_A_REDIRECT_DECLARES;
    }
  }

  private final GrowPaymentProcesses processes;
  private final CompleteHostedProcess completions;
  private final GrowPayerCards cards;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  GrowHostedCheckout(
      GrowPaymentProcesses processes,
      CompleteHostedProcess completions,
      GrowPayerCards cards,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    this.processes = processes;
    this.completions = completions;
    this.cards = cards;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
  }

  Rendered open(@Nullable String hostedLink) {
    Optional<GrowPaymentProcess> found = theProcessOf(hostedLink);
    if (found.isEmpty()) {
      return unknownLink();
    }
    GrowPaymentProcess process = found.get();
    return new Rendered(
        OFFERED,
        GrowHostedPage.CONTENT_TYPE,
        null,
        GrowHostedPage.offering(process, cards.offeredBy(process.testRunId())));
  }

  Rendered submit(
      @Nullable String hostedLink, @Nullable String outcome, @Nullable String cardAlias) {
    Optional<GrowPaymentProcess> found = theProcessOf(hostedLink);
    if (found.isEmpty()) {
      return unknownLink();
    }
    GrowPaymentProcess process = found.get();
    PayerOutcome payerOutcome;
    try {
      payerOutcome = PayerOutcome.declared(outcome == null ? "" : outcome);
    } catch (IllegalArgumentException notAnOutcome) {
      return refusal(process, notAnOutcome.getMessage(), cardAlias, outcome);
    }

    recordInbound(process, outcome, cardAlias);
    CompleteHostedProcess.Result result =
        completions.complete(
            process, payerOutcome, payerOutcome.namesACard() ? cardAlias : null, origin());
    return switch (result) {
      case CompleteHostedProcess.Result.Completed completed -> {
        String location = GrowHostedPage.redirectAfter(completed.process(), payerOutcome);
        recordOutbound(process, REDIRECTED, ProviderOutcome.SUCCESS, completed.process());
        yield new Rendered(REDIRECTED, GrowHostedPage.CONTENT_TYPE, location, "");
      }
      case CompleteHostedProcess.Result.Refused refused -> {
        recordOutbound(process, REFUSED, ProviderOutcome.FAILURE, process);
        yield new Rendered(
            REFUSED,
            GrowHostedPage.CONTENT_TYPE,
            null,
            GrowHostedPage.refusing(refused.inItsOwnWords()));
      }
      case CompleteHostedProcess.Result.NoSuchProcess _ -> unknownLink();
    };
  }

  private Rendered refusal(
      GrowPaymentProcess process,
      @Nullable String words,
      @Nullable String cardAlias,
      @Nullable String outcome) {
    recordInbound(process, outcome, cardAlias);
    recordOutbound(process, REFUSED, ProviderOutcome.FAILURE, process);
    return new Rendered(
        REFUSED,
        GrowHostedPage.CONTENT_TYPE,
        null,
        GrowHostedPage.refusing(words == null ? GrowHostedPage.NO_SUCH_LINK : words));
  }

  private Optional<GrowPaymentProcess> theProcessOf(@Nullable String hostedLink) {
    if (hostedLink == null || hostedLink.isBlank()) {
      return Optional.empty();
    }
    return processes.findByHostedLink(hostedLink);
  }

  private static Rendered unknownLink() {
    return new Rendered(
        NO_SUCH_LINK,
        GrowHostedPage.CONTENT_TYPE,
        null,
        GrowHostedPage.refusing(GrowHostedPage.NO_SUCH_LINK));
  }

  static ObjectOrigin origin() {
    return ObjectOrigin.PROVIDER_API;
  }

  private void recordInbound(
      GrowPaymentProcess process, @Nullable String outcome, @Nullable String cardAlias) {
    audit.record(
        record(
            process,
            AuditDirection.INBOUND_REQUEST,
            null,
            POST,
            null,
            null,
            null,
            submitted(process, outcome, cardAlias)));
  }

  private void recordOutbound(
      GrowPaymentProcess process,
      int httpStatus,
      ProviderOutcome outcome,
      GrowPaymentProcess answered) {
    audit.record(
        record(
            process,
            AuditDirection.OUTBOUND_RESPONSE,
            outcome,
            POST,
            httpStatus,
            answered.processId(),
            outcome == ProviderOutcome.FAILURE
                ? RefusalSubject.theObject(answered.processId())
                : null,
            Redaction.redact(
                Map.of("normalizedStatus", List.of(answered.normalizedStatus().name())),
                GrowFields.SENSITIVE_RESPONSE_FIELDS)));
  }

  private static Map<String, List<String>> submitted(
      GrowPaymentProcess process, @Nullable String outcome, @Nullable String cardAlias) {
    LinkedHashMap<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(GrowHostedPage.LINK, List.of(process.hostedLink()));
    fields.put(GrowHostedPage.OUTCOME, List.of(outcome == null ? "" : outcome));
    if (cardAlias != null) {
      fields.put(GrowHostedPage.CARD, List.of(cardAlias));
    }
    return Redaction.redact(fields, GrowFields.SENSITIVE_REQUEST_FIELDS);
  }

  private AuditRecord record(
      GrowPaymentProcess process,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      String method,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> body) {
    TestRunId testRunId = process.testRunId();
    return new AuditRecord(
        "aud_" + ids.hex(AUDIT_ID_LENGTH),
        testRunId,
        null,
        GrowLightApi.GROW,
        process.merchantId(),
        origin(),
        direction,
        outcome,
        method,
        GrowHostedPage.PATH,
        httpStatus,
        providerObjectId,
        refusal,
        null,
        1,
        clock.now(testRunId),
        Map.of(),
        body,
        null);
  }
}
