package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.repro.ClaimedProviderNativeId;
import dev.marcinromanowski.pspsim.kernel.repro.ProviderNativeIds;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionManifest;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionReport;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ReproductionAdmission {

  public static final String THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK =
      "the reproduction bundle this request asked this provider to take back";

  public static final String AN_IMPORT_TAKES_BACK_A_BUNDLE =
      "bundle must carry the reproduction bundle this import takes back, because an import naming"
          + " none has nothing to create, update or skip and would be reported as an import that"
          + " did nothing rather than as a request that named nothing";

  public static final String A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES =
      "setup must carry the run setup this bundle reproduces, because the evidence beside it is"
          + " read and never written, so a bundle without a setup is a document an import would"
          + " report as having created nothing while a reader believed it had restored a run";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Refused(List<BundleRefusal> refusals) implements Verdict {

      public Refused {
        refusals = List.copyOf(refusals);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Conflicting(List<BundleRefusal> refusals) implements Verdict {

      public Conflicting {
        refusals = List.copyOf(refusals);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Taken(ReproductionReport report) implements Verdict {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  private final BundleAdmission setups;
  private final PaymentMethodFixtures fixtures;
  private final TestRunId whenNoTestRunIsNamed;

  public ReproductionAdmission(
      BundleAdmission setups, PaymentMethodFixtures fixtures, TestRunId whenNoTestRunIsNamed) {
    this.setups = setups;
    this.fixtures = fixtures;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Verdict takeBack(SimulatorProvider module, DeclaredReproductionImport declared) {
    ReproductionBundle bundle = declared.bundle();
    if (bundle == null) {
      return new Verdict.Rejected(AN_IMPORT_TAKES_BACK_A_BUNDLE);
    }
    RunBundle setup = bundle.setup();
    if (setup == null) {
      return new Verdict.Rejected(A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES);
    }

    TestRunId run;
    try {
      run =
          TestRunId.declaredOr(
              declared.testRunId() == null ? setup.testRunId() : declared.testRunId(),
              whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }

    Set<String> shared = theKeysManyFixturesMayShare(module, setup.carriedFixtures());
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            setup.carriedFixtures(),
            declared.theRemappingItAsksFor(),
            shared,
            theIdsThisRunAlreadyClaims(module, run, shared));
    if (preservation instanceof ProviderNativeIds.Preservation.Refused refused) {
      return new Verdict.Refused(refused.refusals());
    }
    if (preservation instanceof ProviderNativeIds.Preservation.Conflicting conflicting) {
      return new Verdict.Conflicting(conflicting.refusals());
    }
    ProviderNativeIds.Preservation.Preserved preserved =
        (ProviderNativeIds.Preservation.Preserved) preservation;

    DeclaredBundleImport takingTheSetupBack =
        new DeclaredBundleImport(
            run.value(),
            declared.writesNothing(),
            setup.carryingInstead(preserved.fixtures()),
            declared.theSecretsItSupplies());

    return switch (setups.takeBack(module, takingTheSetupBack)) {
      case BundleAdmission.Verdict.Rejected rejected -> new Verdict.Rejected(rejected.message());
      case BundleAdmission.Verdict.Refused refused -> new Verdict.Refused(refused.refusals());
      case BundleAdmission.Verdict.Conflicting conflicting ->
          new Verdict.Conflicting(conflicting.refusals());
      case BundleAdmission.Verdict.Taken taken ->
          new Verdict.Taken(theReportOf(bundle, taken.report(), preserved.remapped()));
    };
  }

  public static ReproductionReport theReportOf(
      ReproductionBundle bundle, BundleReport setup, List<String> remapped) {
    ReproductionManifest contents = bundle.contents();
    return ReproductionReport.of(
        setup,
        contents == null ? ReproductionManifest.of(bundle.setup(), bundle.evidence()) : contents,
        remapped);
  }

  public static Set<String> theKeysManyFixturesMayShare(
      SimulatorProvider module, List<PortableFixture> carried) {
    ProviderPaymentMethodFields declared = module.paymentMethodFields();
    Set<String> shared = new LinkedHashSet<>();
    for (PortableFixture fixture : carried) {
      for (String key : fixture.carriedProviderIds().keySet()) {
        if (declared.namesAnOwner(key)) {
          shared.add(key);
        }
      }
    }
    return Set.copyOf(shared);
  }

  private List<ClaimedProviderNativeId> theIdsThisRunAlreadyClaims(
      SimulatorProvider module, TestRunId run, Set<String> shared) {
    List<ClaimedProviderNativeId> claimed = new ArrayList<>();
    for (PaymentMethodFixture held : fixtures.of(module.id(), run)) {
      for (Map.Entry<String, String> id : held.providerIds().entrySet()) {
        if (shared.contains(id.getKey())
            || module.paymentMethodFields().namesAnOwner(id.getKey())) {
          continue;
        }
        claimed.add(new ClaimedProviderNativeId(id.getKey(), id.getValue(), held.fixtureAlias()));
      }
    }
    return List.copyOf(claimed);
  }
}
