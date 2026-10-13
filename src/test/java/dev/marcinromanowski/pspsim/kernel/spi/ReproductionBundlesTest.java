package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionManifest;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ReproductionBundlesTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final String CHARGE = "ch_1";

  private final BundleStores.Accounts accounts = new BundleStores.Accounts();
  private final BoundRouteAliases aliases = new BoundRouteAliases();
  private final InMemoryTestRunSettings settings = new InMemoryTestRunSettings();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final BundleStores.Pins pins = new BundleStores.Pins();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final Trail trail = new Trail();

  private final ProviderDouble provider = new ProviderDouble().holding(CHARGE);

  @Test
  void a_capture_naming_no_payment_is_rejected_rather_than_answered_with_the_whole_run() {
    assertThat(captures().of(provider, RUN.value(), null))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(ReproductionBundles.A_REPRODUCTION_BUNDLE_IS_ABOUT_ONE_PAYMENT));
    assertThat(captures().of(provider, RUN.value(), "   "))
        .isInstanceOf(ReproductionBundles.Capture.Rejected.class);
  }

  @Test
  void a_test_run_id_no_namespace_can_hold_is_rejected_rather_than_read_from() {
    assertThat(captures().of(provider, "a run with spaces", CHARGE))
        .isInstanceOf(ReproductionBundles.Capture.Rejected.class);
  }

  @Test
  void a_payment_no_object_of_this_run_answers_to_is_not_found_rather_than_captured_empty() {
    assertThat(captures().of(provider, RUN.value(), "ch_nothing"))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Unknown.class,
            unknown ->
                assertThat(unknown.message())
                    .isEqualTo(ReproductionBundles.NO_OBJECT_OF_THIS_RUN_ANSWERS_TO_IT));
  }

  @Test
  void a_capture_carries_the_setup_of_the_run_the_evidence_of_the_payment_and_both_content_lists() {
    pins.pin(RUN, PINNED);
    trail.holding(aRow("aud_1", CHARGE), aRow("aud_2", "ch_someone_else"));

    assertThat(captures().of(provider, RUN.value(), CHARGE))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Captured.class,
            captured -> {
              ReproductionBundle bundle = captured.bundle();
              assertThat(bundle.testRunId()).isEqualTo(RUN.value());
              assertThat(bundle.provider()).isEqualTo(ProviderDouble.ID.value());
              assertThat(bundle.capturedAt()).isEqualTo(PINNED.toString());
              assertThat(bundle.setup()).isNotNull();
              assertThat(bundle.evidence()).isNotNull();
              assertThat(bundle.evidence().key()).isEqualTo(CHARGE);
              assertThat(bundle.evidence().effectiveClock()).isEqualTo(PINNED.toString());
              assertThat(bundle.evidence().carriedTrail()).hasSize(1);
              assertThat(bundle.contents()).isNotNull();
              assertThat(bundle.contents().theContentNamesAnImportWrites())
                  .contains(ReproductionManifest.ACCOUNTS);
              assertThat(bundle.contents().theContentNamesNoImportWrites())
                  .contains(ReproductionManifest.TRAIL);
            });
  }

  @Test
  void a_trail_read_that_ran_out_of_pages_says_so_on_the_bundle_it_captured() {
    trail.answeringNoPage();

    assertThat(captures().of(provider, RUN.value(), CHARGE))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Captured.class,
            captured -> assertThat(captured.bundle().evidence().trailTruncated()).isTrue());
  }

  @Test
  void a_trail_longer_than_the_pages_one_capture_reads_says_so_rather_than_carrying_a_short_one() {
    trail.answeringAFullPageEveryTime();

    assertThat(captures().of(provider, RUN.value(), CHARGE))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Captured.class,
            captured -> {
              assertThat(captured.bundle().evidence().trailTruncated()).isTrue();
              assertThat(trail.pagesRead()).isEqualTo(ReproductionBundles.PAGES_ONE_CAPTURE_READS);
            });
  }

  @Test
  void a_run_whose_setup_cannot_be_exported_whole_is_refused_rather_than_captured_in_part() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            ProviderDouble.ID,
            RUN,
            "merchant-1",
            "owner-1",
            PaymentMethodKind.CARD,
            "visa-success",
            new FixtureCard("visa", "4242", 12, 2031, "credit"),
            Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
            "scn_this_run_no_longer_holds",
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    assertThat(captures().of(provider, RUN.value(), CHARGE))
        .isInstanceOfSatisfying(
            ReproductionBundles.Capture.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[0].defaultScenario"));
  }

  @Test
  void the_refusals_a_refused_capture_was_built_from_do_not_change_it_afterwards() {
    List<BundleRefusal> refusals =
        new ArrayList<>(List.of(new BundleRefusal("fixtures[0].defaultScenario", "no")));
    ReproductionBundles.Capture.Refused refused = new ReproductionBundles.Capture.Refused(refusals);
    refusals.add(new BundleRefusal("fixtures[1].defaultScenario", "no"));

    assertThat(refused.refusals()).hasSize(1);
  }

  private ReproductionBundles captures() {
    RunBundles setups =
        new RunBundles(
            accounts,
            aliases,
            settings,
            declaredSettings(),
            fixtures,
            rules,
            pins,
            pins,
            DEFAULT_RUN);
    return new ReproductionBundles(setups, trail, deliveries, pins, DEFAULT_RUN);
  }

  private DeclaredProviderSettings declaredSettings() {
    return new DeclaredProviderSettings(
        List.of(
            new ProviderSettings(
                ProviderDouble.ID,
                List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S")))));
  }

  private static AuditRecord aRow(String auditId, @Nullable String providerObjectId) {
    return new AuditRecord(
        auditId,
        RUN,
        null,
        ProviderDouble.ID,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        "/v1/charges",
        201,
        providerObjectId,
        null,
        null,
        1,
        PINNED,
        Map.of(),
        Map.of("amount", List.of("1000")),
        null);
  }

  private static final class Trail implements AuditReads {

    private final List<AuditRecord> held = new ArrayList<>();
    private boolean answeringNoPage;
    private boolean answeringAFullPageEveryTime;
    private int pagesRead;

    void holding(AuditRecord... records) {
      held.addAll(List.of(records));
    }

    void answeringNoPage() {
      answeringNoPage = true;
    }

    void answeringAFullPageEveryTime() {
      answeringAFullPageEveryTime = true;
    }

    int pagesRead() {
      return pagesRead;
    }

    @Override
    public AuditPage pageOf(ProviderId provider, AuditQuery query) {
      pagesRead++;
      if (answeringNoPage) {
        return new AuditPage.NoSuchCursor();
      }
      if (!answeringAFullPageEveryTime) {
        return new AuditPage.Listed(List.copyOf(held));
      }
      List<AuditRecord> full = new ArrayList<>();
      for (int row = 0; row < AuditQuery.LARGEST_LIMIT; row++) {
        full.add(aRow("aud_" + pagesRead + "_" + row, CHARGE));
      }
      return new AuditPage.Listed(List.copyOf(full));
    }

    @Override
    public List<AuditRecord> latestOf(
        ProviderId provider, TestRunId testRunId, @Nullable ObjectOrigin origin, int limit) {
      return List.copyOf(held);
    }

    @Override
    public List<TestRunActivity> theRunsNewestTouchFirst() {
      return List.of();
    }
  }
}
