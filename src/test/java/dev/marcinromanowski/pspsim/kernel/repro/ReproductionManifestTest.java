package dev.marcinromanowski.pspsim.kernel.repro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleEntry;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableCard;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableRouteAlias;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class ReproductionManifestTest {

  @Test
  void every_part_of_the_setup_is_listed_as_something_an_import_writes() {
    ReproductionManifest manifest = ReproductionManifest.of(aSetup(), null);

    assertThat(manifest.theContentNamesAnImportWrites())
        .containsExactly(
            ReproductionManifest.ACCOUNTS,
            ReproductionManifest.WEBHOOK_TARGETS,
            ReproductionManifest.ROUTE_ALIASES,
            ReproductionManifest.SETTINGS,
            ReproductionManifest.CLOCK,
            ReproductionManifest.FIXTURES,
            ReproductionManifest.SCENARIO_RULES);
    assertThat(manifest.theContentsAnImportWrites())
        .allSatisfy(
            content ->
                assertThat(content.why()).isEqualTo(ReproductionManifest.AN_IMPORT_WRITES_IT));
  }

  @Test
  void every_part_of_the_evidence_is_listed_as_something_no_import_writes() {
    ReproductionManifest manifest = ReproductionManifest.of(null, someEvidence());

    assertThat(manifest.theContentNamesNoImportWrites())
        .containsExactly(
            ReproductionManifest.STATE_SNAPSHOT,
            ReproductionManifest.LEDGER,
            ReproductionManifest.TRAIL,
            ReproductionManifest.PROVIDER_EXCHANGES,
            ReproductionManifest.CALLBACKS,
            ReproductionManifest.CALLBACK_ATTEMPTS);
    assertThat(manifest.theContentsNoImportWrites())
        .noneSatisfy(
            content ->
                assertThat(content.why()).isEqualTo(ReproductionManifest.AN_IMPORT_WRITES_IT));
  }

  @Test
  void no_content_is_listed_both_as_written_and_as_only_read() {
    ReproductionManifest manifest = ReproductionManifest.of(aSetup(), someEvidence());

    assertThat(manifest.theContentNamesAnImportWrites())
        .doesNotContainAnyElementsOf(manifest.theContentNamesNoImportWrites());
  }

  @Test
  void the_setup_counts_what_each_of_its_parts_carries() {
    ReproductionManifest manifest = ReproductionManifest.of(aSetup(), null);

    assertThat(manifest.theContentsAnImportWrites())
        .extracting(CarriedContent::content, CarriedContent::carried)
        .containsExactly(
            Tuple.tuple(ReproductionManifest.ACCOUNTS, 1),
            Tuple.tuple(ReproductionManifest.WEBHOOK_TARGETS, 1),
            Tuple.tuple(ReproductionManifest.ROUTE_ALIASES, 1),
            Tuple.tuple(ReproductionManifest.SETTINGS, 2),
            Tuple.tuple(ReproductionManifest.CLOCK, 1),
            Tuple.tuple(ReproductionManifest.FIXTURES, 1),
            Tuple.tuple(ReproductionManifest.SCENARIO_RULES, 0));
  }

  @Test
  void a_setup_standing_on_no_pinned_clock_carries_no_clock_to_write() {
    RunBundle releasing =
        new RunBundle(null, "run-a", "twokeys", null, null, null, null, null, null, null, null);

    assertThat(ReproductionManifest.of(releasing, null).theContentsAnImportWrites())
        .filteredOn(content -> content.content().equals(ReproductionManifest.CLOCK))
        .singleElement()
        .satisfies(content -> assertThat(content.carried()).isZero());
  }

  @Test
  void the_evidence_counts_the_rows_the_callbacks_the_attempts_and_the_operations_apart() {
    ReproductionManifest manifest = ReproductionManifest.of(null, someEvidence());

    assertThat(manifest.theContentsNoImportWrites())
        .extracting(CarriedContent::content, CarriedContent::carried)
        .containsExactly(
            Tuple.tuple(ReproductionManifest.STATE_SNAPSHOT, 1),
            Tuple.tuple(ReproductionManifest.LEDGER, 2),
            Tuple.tuple(ReproductionManifest.TRAIL, 3),
            Tuple.tuple(ReproductionManifest.PROVIDER_EXCHANGES, 2),
            Tuple.tuple(ReproductionManifest.CALLBACKS, 1),
            Tuple.tuple(ReproductionManifest.CALLBACK_ATTEMPTS, 2));
  }

  @Test
  void evidence_that_reached_no_object_still_lists_a_state_snapshot_carrying_nothing() {
    ReproductionEvidence nothing =
        new ReproductionEvidence("pi_1", null, null, null, null, null, false);

    assertThat(ReproductionManifest.of(null, nothing).theContentsNoImportWrites())
        .extracting(CarriedContent::carried)
        .containsOnly(0);
  }

  @Test
  void a_signed_message_is_the_reason_the_exchanges_the_callbacks_and_the_attempts_are_read_only() {
    ReproductionManifest manifest = ReproductionManifest.of(null, someEvidence());

    assertThat(manifest.theContentsNoImportWrites())
        .filteredOn(
            content ->
                List.of(
                        ReproductionManifest.PROVIDER_EXCHANGES,
                        ReproductionManifest.CALLBACKS,
                        ReproductionManifest.CALLBACK_ATTEMPTS)
                    .contains(content.content()))
        .hasSize(3)
        .allSatisfy(
            content ->
                assertThat(content.why())
                    .isEqualTo(
                        ReproductionManifest
                            .A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT));
  }

  @Test
  void a_manifest_of_neither_half_lists_nothing_either_way() {
    ReproductionManifest manifest = ReproductionManifest.of(null, null);

    assertThat(manifest.theContentsAnImportWrites()).isEmpty();
    assertThat(manifest.theContentsNoImportWrites()).isEmpty();
  }

  @Test
  void a_manifest_reading_a_document_that_listed_nothing_answers_two_empty_lists() {
    ReproductionManifest manifest = new ReproductionManifest(null, null);

    assertThat(manifest.theContentNamesAnImportWrites()).isEmpty();
    assertThat(manifest.theContentNamesNoImportWrites()).isEmpty();
  }

  @Test
  void a_content_no_name_could_be_read_from_is_refused_when_it_is_built() {
    assertThatIllegalArgumentException().isThrownBy(() -> new CarriedContent(" ", 1, "because"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CarriedContent("trail", -1, "because"));
  }

  @Test
  void a_bundle_names_its_own_contents_when_it_is_captured() {
    ReproductionBundle bundle =
        ReproductionBundle.of("run-a", "twokeys", "2026-09-25T00:00:00Z", aSetup(), someEvidence());

    assertThat(bundle.contents()).isNotNull();
    assertThat(bundle.contents().theContentNamesAnImportWrites())
        .contains(ReproductionManifest.FIXTURES);
    assertThat(bundle.contents().theContentNamesNoImportWrites())
        .contains(ReproductionManifest.TRAIL);
  }

  @Test
  void a_report_repeats_the_two_lists_the_bundle_named_and_says_whether_an_id_was_remapped() {
    ReproductionReport preserved =
        ReproductionReport.of(
            BundleReport.of("run-b", "twokeys", true, List.of(), List.of()),
            ReproductionManifest.of(aSetup(), someEvidence()),
            List.of());

    assertThat(preserved.providerNativeIdsPreserved()).isTrue();
    assertThat(preserved.theContentsItWrote()).contains(ReproductionManifest.ACCOUNTS);
    assertThat(preserved.theContentsItOnlyRead()).contains(ReproductionManifest.CALLBACKS);
    assertThat(preserved.applied()).isTrue();

    ReproductionReport remapped =
        ReproductionReport.of(
            BundleReport.of(
                "run-b",
                "twokeys",
                false,
                List.of(BundleEntry.created("fixture", "visa")),
                List.of()),
            ReproductionManifest.of(aSetup(), someEvidence()),
            List.of("token"));

    assertThat(remapped.providerNativeIdsPreserved()).isFalse();
    assertThat(remapped.theProviderNativeIdKeysItRemapped()).containsExactly("token");
    assertThat(remapped.applied()).isFalse();
  }

  private static RunBundle aSetup() {
    return new RunBundle(
        null,
        "run-a",
        "twokeys",
        "2026-09-25T00:00:00Z",
        new DeclaredClock("2026-09-24T12:00:00Z", null, null),
        List.of(new PortableAccount("merchant-1", Map.of(), List.of())),
        List.of(new PortableWebhookTarget("merchant-1", "https://merchant.test/hook")),
        List.of(new PortableRouteAlias("from", "to")),
        Map.of("a", "1", "b", "2"),
        List.of(
            new PortableFixture(
                "merchant-1",
                "owner-1",
                "card",
                "visa-success",
                new PortableCard("visa", "4242", 12, 2031, "credit"),
                Map.of("token", "tok_1"),
                null)),
        null);
  }

  private static ReproductionEvidence someEvidence() {
    return new ReproductionEvidence(
        "pi_1",
        "providerObjectId",
        "2026-09-24T12:00:00Z",
        new RecordedObject(
            "pi_1",
            List.of("pi_1"),
            "succeeded",
            "SUCCEEDED",
            "10.00",
            "USD",
            false,
            false,
            true,
            "PROVIDER_API",
            "2026-09-24T12:00:00Z",
            Map.of(),
            List.of(
                new RecordedOperation(
                    1,
                    "AUTHORIZE",
                    "10.00",
                    true,
                    null,
                    "succeeded",
                    "SUCCEEDED",
                    "2026-09-24T12:00:00Z"),
                new RecordedOperation(
                    2,
                    "CAPTURE",
                    "10.00",
                    null,
                    null,
                    "succeeded",
                    "SUCCEEDED",
                    "2026-09-24T12:00:01Z"))),
        List.of(
            new RecordedExchange(
                0,
                "aud_1",
                "PROVIDER_API",
                "INBOUND_REQUEST",
                "POST",
                "/v1/x",
                null,
                null,
                null,
                null,
                null,
                1,
                "2026-09-24T12:00:00Z",
                Map.of("Idempotency-Key", List.of("k")),
                Map.of()),
            new RecordedExchange(
                1,
                "aud_2",
                "PROVIDER_API",
                "OUTBOUND_RESPONSE",
                "POST",
                "/v1/x",
                200,
                "SUCCESS",
                null,
                null,
                null,
                1,
                "2026-09-24T12:00:01Z",
                Map.of(),
                Map.of("id", List.of("pi_1"))),
            new RecordedExchange(
                2,
                "aud_3",
                "CONTROL_API",
                "INBOUND_REQUEST",
                "GET",
                "/state/pi_1",
                200,
                null,
                null,
                null,
                null,
                1,
                "2026-09-24T12:00:02Z",
                null,
                null)),
        List.of(
            new RecordedCallback(
                "whd_1",
                "evt_1",
                "payment_intent.succeeded",
                "pi_1",
                "DELIVERED",
                "https://merchant.test/hook",
                2,
                3,
                null,
                null,
                null,
                "application/json",
                Map.of("id", List.of("evt_1")),
                List.of(
                    new RecordedAttempt(
                        1,
                        "2026-09-24T12:00:03Z",
                        "https://merchant.test/hook",
                        "REFUSED",
                        500,
                        null,
                        "boom",
                        Map.of("Stripe-Signature", List.of("t=1,v1=deadbeef"))),
                    new RecordedAttempt(
                        2,
                        "2026-09-24T12:00:04Z",
                        "https://merchant.test/hook",
                        "ACKNOWLEDGED",
                        200,
                        null,
                        null,
                        Map.of())))),
        false);
  }
}
