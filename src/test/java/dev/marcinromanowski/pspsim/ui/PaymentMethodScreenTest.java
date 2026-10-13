package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.RunBundles;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentMethodScreenTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

  private static final PaymentMethodScreen.WhatTheProviderHoldsUnderTheseIds NOTHING_HELD_YET =
      _ -> List.of();

  @Test
  void a_tester_who_seeded_a_card_sees_its_alias_owner_and_masked_card_in_the_list() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(), RUN.value(), List.of(aVisaCard()), List.of(), NOTHING_HELD_YET);

    assertThat(view.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.fixtureAlias()).isEqualTo("visa-success");
              assertThat(row.ownerId()).isEqualTo("customer-123");
              assertThat(row.merchantId()).isEqualTo("merchant-1");
              assertThat(row.type()).isEqualTo("card");
              assertThat(row.brand()).isEqualTo("visa");
              assertThat(row.maskedCard()).isEqualTo("**** 1111");
              assertThat(row.expiry()).isEqualTo("12/2030");
              assertThat(row.funding()).isEqualTo("credit");
              assertThat(row.describesACard()).isTrue();
            });
  }

  @Test
  void a_single_digit_expiry_month_is_shown_the_way_a_card_prints_it() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(fixture(new FixtureCard("visa", "1111", 3, 2030, "credit"))),
            List.of(),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().expiry()).isEqualTo("03/2030");
  }

  @Test
  void a_two_digit_expiry_month_is_shown_without_a_leading_zero() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(fixture(new FixtureCard("visa", "1111", 10, 2030, "credit"))),
            List.of(),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().expiry()).isEqualTo("10/2030");
  }

  @Test
  void the_provider_native_ids_a_tester_must_copy_into_a_client_are_shown_beside_the_card() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(), RUN.value(), List.of(aVisaCard()), List.of(), NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().providerIds())
        .singleElement()
        .satisfies(
            named -> {
              assertThat(named.key()).isEqualTo(ProviderPaymentMethodFields.TOKEN);
              assertThat(named.value()).isEqualTo("tok_sim_123");
              assertThat(named.namesAnObjectTheProviderHolds()).isFalse();
            });
  }

  @Test
  void
      what_the_provider_holds_under_each_id_is_shown_beside_it_so_a_detached_method_reads_as_ended() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aVisaCard()),
            List.of(),
            ids ->
                theObjectsAnsweringTo(
                    ids, "payment_method customer=null", NormalizedStatus.CANCELLED));

    assertThat(view.rows().getFirst().providerIds())
        .singleElement()
        .satisfies(
            named -> {
              assertThat(named.namesAnObjectTheProviderHolds()).isTrue();
              assertThat(named.nativeStatus()).isEqualTo("payment_method customer=null");
              assertThat(named.normalizedStatus())
                  .isEqualTo(new StatusBadge("CANCELLED", StatusBadge.Tone.NEUTRAL));
            });
  }

  @Test
  void two_fixtures_that_name_one_owner_id_both_show_the_object_that_owner_id_names() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aCardOwnedBy("fx_1", "a-card"), aCardOwnedBy("fx_2", "a-second-card")),
            List.of(),
            ids -> theObjectsAnsweringTo(ids, "customer", NormalizedStatus.SUCCEEDED));

    assertThat(view.rows()).hasSize(2);
    assertThat(view.rows().get(0).providerIds().getFirst().value())
        .isEqualTo(view.rows().get(1).providerIds().getFirst().value());
    assertThat(view.rows().get(1).providerIds().getFirst().nativeStatus()).isEqualTo("customer");
  }

  private static PaymentMethodFixture aCardOwnedBy(String fixtureId, String alias) {
    return new PaymentMethodFixture(
        fixtureId,
        PROVIDER,
        RUN,
        "merchant-1",
        "customer-123",
        PaymentMethodKind.CARD,
        alias,
        new FixtureCard("visa", "1111", 12, 2030, "credit"),
        Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "cus_one_owner"),
        null,
        AT);
  }

  @Test
  void the_screen_asks_the_store_once_for_every_identifier_every_fixture_it_lists_names() {
    List<List<String>> asked = new ArrayList<>();

    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aVisaCard(), aCardOwnedBy("fx_2", "a-second-card"), aVisaCard()),
            List.of(),
            ids -> {
              asked.add(ids);
              return List.of();
            });

    assertThat(asked)
        .as(
            "a screen that asks the store once per identifier pays a lookup for every"
                + " provider-native id every seeded method carries, and this provider's lookup"
                + " walks each of its stores: %s",
            asked)
        .hasSize(1);
    assertThat(asked.getFirst()).containsExactly("tok_sim_123", "cus_one_owner", "tok_sim_123");
    assertThat(view.rows()).hasSize(3);
  }

  @Test
  void a_screen_listing_no_fixture_at_all_asks_the_store_for_no_identifier() {
    List<List<String>> asked = new ArrayList<>();

    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(),
            List.of(),
            ids -> {
              asked.add(ids);
              return List.of();
            });

    assertThat(view.rows()).isEmpty();
    assertThat(asked)
        .as(
            "a screen with nothing to label has nothing to ask for, and asking the store for an"
                + " empty list of identifiers is a read of every store this provider keeps for an"
                + " answer that is empty before it is asked")
        .isEmpty();
  }

  @Test
  void an_identifier_an_object_only_later_answered_to_is_labelled_with_that_objects_status() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aVisaCard()),
            List.of(),
            _ ->
                List.of(
                    anObjectWhoseIdentityIs(
                        ProviderObjectIdentity.of("tok_sim_000").answeringAlsoTo("tok_sim_123"),
                        "a token this provider minted a second name for",
                        NormalizedStatus.SUCCEEDED)));

    assertThat(view.rows().getFirst().providerIds())
        .singleElement()
        .satisfies(
            named -> {
              assertThat(named.value()).isEqualTo("tok_sim_123");
              assertThat(named.namesAnObjectTheProviderHolds())
                  .as(TheObjectAnIdentifierNames.THE_ASKED_IDENTIFIER_IS_THE_KEY)
                  .isTrue();
              assertThat(named.nativeStatus())
                  .isEqualTo("a token this provider minted a second name for");
            });
  }

  @Test
  void a_wallet_fixture_says_it_describes_no_card_rather_than_showing_an_empty_mask() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(
                new PaymentMethodFixture(
                    "fx_2",
                    PROVIDER,
                    RUN,
                    "merchant-1",
                    "customer-123",
                    PaymentMethodKind.WALLET,
                    "a-wallet",
                    null,
                    Map.of(),
                    null,
                    AT)),
            List.of(),
            NOTHING_HELD_YET);

    assertThat(view.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.describesACard()).isFalse();
              assertThat(row.maskedCard()).isNull();
              assertThat(row.namesAScenario()).isFalse();
            });
  }

  @Test
  void a_fixture_that_names_a_scenario_shows_the_rule_it_names() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aFixtureNaming("scn_1")),
            aRunHolding("scn_1"),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().namesAScenario()).isTrue();
    assertThat(view.rows().getFirst().defaultScenario()).isEqualTo("scn_1");
  }

  @Test
  void a_fixture_naming_a_rule_this_run_still_holds_is_not_marked_as_naming_a_deleted_one() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aFixtureNaming("scn_1")),
            aRunHolding("scn_1"),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().namesARuleThisRunNoLongerHolds()).isFalse();
  }

  @Test
  void a_fixture_naming_a_rule_this_run_no_longer_holds_is_marked_rather_than_printed_as_live() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aFixtureNaming("scn_1")),
            aRunHolding("scn_2"),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().namesAScenario()).isTrue();
    assertThat(view.rows().getFirst().defaultScenario()).isEqualTo("scn_1");
    assertThat(view.rows().getFirst().namesARuleThisRunNoLongerHolds()).isTrue();
  }

  @Test
  void a_fixture_naming_no_rule_at_all_is_not_marked_as_naming_one_this_run_deleted() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(),
            RUN.value(),
            List.of(aFixtureNaming(null)),
            aRunHolding("scn_1"),
            NOTHING_HELD_YET);

    assertThat(view.rows().getFirst().namesAScenario()).isFalse();
    assertThat(view.rows().getFirst().namesARuleThisRunNoLongerHolds()).isFalse();
  }

  @Test
  void the_marked_row_says_what_the_run_bundle_says_rather_than_a_second_wording_of_it() {
    PaymentMethodScreen.View view =
        PaymentMethodScreen.of(
            PROVIDER.value(), RUN.value(), List.of(), List.of(), NOTHING_HELD_YET);

    assertThat(view.theRefusalOfARuleThisRunNoLongerHolds())
        .isEqualTo(RunBundles.A_RULE_THIS_RUN_NO_LONGER_HOLDS);
  }

  private static List<ProviderObjectState> theObjectsAnsweringTo(
      List<String> providerObjectIds, String nativeStatus, NormalizedStatus normalizedStatus) {
    List<ProviderObjectState> held = new ArrayList<>();
    for (String providerObjectId : providerObjectIds) {
      held.add(
          anObjectWhoseIdentityIs(
              ProviderObjectIdentity.of(providerObjectId), nativeStatus, normalizedStatus));
    }
    return List.copyOf(held);
  }

  private static ProviderObjectState anObjectWhoseIdentityIs(
      ProviderObjectIdentity identity, String nativeStatus, NormalizedStatus normalizedStatus) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        nativeStatus,
        normalizedStatus,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.CONTROL_API,
        AT);
  }

  private static List<ScenarioRule> aRunHolding(String scenarioId) {
    return List.of(
        new ScenarioRule(
            scenarioId,
            PROVIDER,
            RUN,
            0,
            1L,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.FixedSuccess(),
            null,
            AT));
  }

  private static PaymentMethodFixture aFixtureNaming(@Nullable String scenarioId) {
    return new PaymentMethodFixture(
        "fx_3",
        PROVIDER,
        RUN,
        "merchant-1",
        "customer-123",
        PaymentMethodKind.CARD,
        "visa-decline",
        new FixtureCard("visa", "1111", 12, 2030, "credit"),
        Map.of(),
        scenarioId,
        AT);
  }

  @Test
  void the_form_draws_the_provider_native_id_the_provider_declares_and_nothing_it_does_not() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(),
            List.of(),
            null);

    assertThat(form.providerIds())
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.key()).isEqualTo(ProviderPaymentMethodFields.TOKEN);
              assertThat(field.label()).isEqualTo("the saved card token");
              assertThat(field.required()).isTrue();
            });
    assertThat(form.readsNoProviderNativeId()).isFalse();
  }

  @Test
  void a_provider_that_reads_no_provider_native_id_draws_no_input_and_says_so() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            new ProviderPaymentMethodFields(
                PROVIDER,
                List.of(),
                Map.of(
                    ProviderPaymentMethodFields.CUSTOMER_ID,
                    "it names no customer",
                    ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                    "it names no payment method",
                    ProviderPaymentMethodFields.TOKEN,
                    "it mints no token")),
            Map.of(),
            List.of(),
            null);

    assertThat(form.providerIds()).isEmpty();
    assertThat(form.readsNoProviderNativeId()).isTrue();
    assertThat(form.keysItRefuses()).hasSize(3);
  }

  @Test
  void the_form_prints_the_providers_own_words_for_every_key_it_refuses() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(),
            List.of(),
            null);

    assertThat(form.keysItRefuses())
        .extracting(PaymentMethodForm.Refused::key, PaymentMethodForm.Refused::instead)
        .containsExactlyInAnyOrder(
            Tuple.tuple(ProviderPaymentMethodFields.CUSTOMER_ID, "it names no customer"),
            Tuple.tuple(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "it calls that a token"));
  }

  @Test
  void the_form_offers_only_the_scenarios_this_test_run_has_already_written() {
    InMemoryScenarioRules scenarios = new InMemoryScenarioRules();
    scenarios.add(
        new NewScenarioRule(
            PROVIDER, RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.FixedSuccess(), null));

    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(),
            scenarios.of(PROVIDER, RUN),
            null);

    assertThat(form.scenariosAlreadyWritten()).containsExactly("scn_1");
  }

  @Test
  void a_refused_provider_native_id_is_marked_on_the_field_a_tester_must_retype() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(PaymentMethodForm.SUBMITTED_ID_PREFIX + "token", "tok_1"),
            List.of(),
            "providerIds.token");

    assertThat(form.providerIds().getFirst().refused()).isTrue();
    assertThat(form.providerIds().getFirst().value()).isEqualTo("tok_1");
  }

  @Test
  void a_field_no_refusal_named_is_not_marked_as_the_one_a_tester_must_retype() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(PaymentMethodForm.SUBMITTED_ID_PREFIX + "token", "tok_1"),
            List.of(),
            "defaultScenario");

    assertThat(form.providerIds().getFirst().refused()).isFalse();
  }

  @Test
  void the_form_offers_every_type_the_route_publishes_and_starts_on_a_card() {
    PaymentMethodForm.View form =
        PaymentMethodForm.of(
            PROVIDER.value(),
            RUN.value(),
            aProviderThatReadsOnlyAToken(),
            Map.of(),
            List.of(),
            null);

    assertThat(form.typesItAccepts()).containsExactly("card", "wallet", "psp-token");
    assertThat(form.type()).isEqualTo("card");
  }

  @Test
  void what_a_tester_typed_becomes_the_same_request_the_control_api_takes() {
    DeclaredPaymentMethodFixture asked =
        PaymentMethodForm.fixtureOf(
            RUN.value(),
            Map.of(
                "merchantId",
                "merchant-1",
                "ownerId",
                "customer-123",
                "type",
                "card",
                "alias",
                "visa-success",
                "brand",
                "visa",
                "last4",
                "1111",
                "expMonth",
                "12",
                "expYear",
                "2030",
                "funding",
                "credit",
                PaymentMethodForm.SUBMITTED_ID_PREFIX + "token",
                "tok_sim_123"));

    assertThat(asked.merchantId()).isEqualTo("merchant-1");
    assertThat(asked.alias()).isEqualTo("visa-success");
    assertThat(asked.card()).isNotNull();
    assertThat(asked.card().expMonth()).isEqualTo(12);
    assertThat(asked.providerIds()).containsExactly(Map.entry("token", "tok_sim_123"));
    assertThat(asked.defaultScenario()).isNull();
  }

  @Test
  void a_wallet_typed_into_the_form_sends_no_card_even_when_the_card_inputs_were_filled_in() {
    DeclaredPaymentMethodFixture asked =
        PaymentMethodForm.fixtureOf(
            RUN.value(),
            Map.of("type", "wallet", "alias", "a-wallet", "brand", "visa", "last4", "1111"));

    assertThat(asked.card()).isNull();
  }

  @Test
  void a_card_form_sent_with_the_expiry_left_empty_asks_for_a_card_that_names_none() {
    DeclaredPaymentMethodFixture asked =
        PaymentMethodForm.fixtureOf(
            RUN.value(),
            Map.of("type", "card", "alias", "a-card", "brand", "visa", "last4", "1111"));

    assertThat(asked.card()).isNotNull();
    assertThat(asked.card().expMonth()).isNull();
    assertThat(asked.card().expYear()).isNull();
  }

  @Test
  void an_expiry_that_is_not_a_number_is_refused_as_typed_rather_than_dropped_as_missing() {
    Map<String, String> submitted =
        Map.of("type", "card", "alias", "a-card", "expMonth", "December");
    String testRunId = RUN.value();

    assertThatThrownBy(() -> PaymentMethodForm.fixtureOf(testRunId, submitted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.expMonth")
        .hasMessageContaining("December");
  }

  private static PaymentMethodFixture aVisaCard() {
    return fixture(new FixtureCard("visa", "1111", 12, 2030, "credit"));
  }

  private static PaymentMethodFixture fixture(FixtureCard card) {
    return new PaymentMethodFixture(
        "fx_1",
        PROVIDER,
        RUN,
        "merchant-1",
        "customer-123",
        PaymentMethodKind.CARD,
        "visa-success",
        card,
        Map.of(ProviderPaymentMethodFields.TOKEN, "tok_sim_123"),
        null,
        AT);
  }

  private static ProviderPaymentMethodFields aProviderThatReadsOnlyAToken() {
    return new ProviderPaymentMethodFields(
        PROVIDER,
        List.of(
            new ConfigurableField(
                ProviderPaymentMethodFields.TOKEN,
                "the saved card token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(
            ProviderPaymentMethodFields.CUSTOMER_ID,
            "it names no customer",
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            "it calls that a token"));
  }
}
