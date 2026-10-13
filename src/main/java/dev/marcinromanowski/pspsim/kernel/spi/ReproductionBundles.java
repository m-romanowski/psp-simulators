package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionEvidence;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ReproductionBundles {

  public static final String KEY = "key";

  public static final int PAGES_ONE_CAPTURE_READS = 8;

  public static final String A_REPRODUCTION_BUNDLE_IS_ABOUT_ONE_PAYMENT =
      "key must name the provider object this bundle reproduces, because a bundle carrying every"
          + " exchange of a test run is a run bundle with a trail stapled to it and says nothing"
          + " about which payment went wrong; the Timeline screen turns a callback event, a related"
          + " field or an idempotency key into the id this asks for";

  public static final String NO_OBJECT_OF_THIS_RUN_ANSWERS_TO_IT =
      "key must name a provider object this provider and test run hold, and nothing of this run"
          + " answers to that value; a run that was never written to and one that was reset answer"
          + " this the same way";

  public sealed interface Capture {

    record Rejected(String message) implements Capture {}

    record Unknown(String message) implements Capture {}

    record Refused(List<BundleRefusal> refusals) implements Capture {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Captured(ReproductionBundle bundle) implements Capture {}
  }

  private final RunBundles setups;
  private final AuditReads audit;
  private final WebhookDeliveries deliveries;
  private final SimulatorClock clock;
  private final TestRunId whenNoTestRunIsNamed;

  public ReproductionBundles(
      RunBundles setups,
      AuditReads audit,
      WebhookDeliveries deliveries,
      SimulatorClock clock,
      TestRunId whenNoTestRunIsNamed) {
    this.setups = setups;
    this.audit = audit;
    this.deliveries = deliveries;
    this.clock = clock;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Capture of(
      SimulatorProvider module, @Nullable String declaredTestRunId, @Nullable String key) {
    TestRunId run;
    try {
      run = TestRunId.declaredOr(declaredTestRunId, whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Capture.Rejected(String.valueOf(rejected.getMessage()));
    }
    if (key == null || key.isBlank()) {
      return new Capture.Rejected(A_REPRODUCTION_BUNDLE_IS_ABOUT_ONE_PAYMENT);
    }
    String addressedBy = key.strip();
    Optional<AddressedProviderObject> addressed =
        AddressedProviderObject.read(module, run, addressedBy);
    if (addressed.isEmpty()) {
      return new Capture.Unknown(NO_OBJECT_OF_THIS_RUN_ANSWERS_TO_IT);
    }

    return switch (setups.of(module, run.value())) {
      case RunBundles.Export.Rejected rejected -> new Capture.Rejected(rejected.message());
      case RunBundles.Export.Refused refused -> new Capture.Refused(refused.refusals());
      case RunBundles.Export.Exported exported ->
          new Capture.Captured(
              ReproductionBundle.of(
                  run.value(),
                  module.id().value(),
                  clock.now(run).toString(),
                  exported.bundle(),
                  theEvidenceOf(module, run, addressedBy, addressed.get())));
    };
  }

  private ReproductionEvidence theEvidenceOf(
      SimulatorProvider module,
      TestRunId run,
      String addressedBy,
      AddressedProviderObject addressed) {
    List<WebhookDelivery> callbacks = deliveries.of(module.id(), run);
    Map<String, List<WebhookAttempt>> attempts = new LinkedHashMap<>();
    for (WebhookDelivery delivery : callbacks) {
      attempts.put(delivery.deliveryId(), deliveries.attemptsOf(delivery.deliveryId()));
    }
    Read read = theTrailOf(module, run);
    return RecordedEvidence.of(
        addressedBy,
        addressed,
        clock.now(run),
        read.records(),
        read.truncated(),
        callbacks,
        Map.copyOf(attempts));
  }

  private record Read(List<AuditRecord> records, boolean truncated) {}

  private Read theTrailOf(SimulatorProvider module, TestRunId run) {
    List<AuditRecord> read = new ArrayList<>();
    String after = null;
    for (int page = 0; page < PAGES_ONE_CAPTURE_READS; page++) {
      AuditQuery query =
          new AuditQuery(
              run,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null,
              null,
              null,
              after,
              AuditQuery.LARGEST_LIMIT);
      if (!(audit.pageOf(module.id(), query) instanceof AuditPage.Listed listed)) {
        return new Read(List.copyOf(read), true);
      }
      read.addAll(listed.records());
      if (listed.records().size() < AuditQuery.LARGEST_LIMIT) {
        return new Read(List.copyOf(read), false);
      }
      after = listed.records().getLast().auditId();
    }
    return new Read(List.copyOf(read), true);
  }
}
