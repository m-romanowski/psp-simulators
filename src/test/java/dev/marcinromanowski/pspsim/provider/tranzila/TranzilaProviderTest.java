package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TranzilaProviderTest {

  private final TranzilaWiring wired = new TranzilaWiring();

  private static final TestRunId OTHER_RUN = TestRunId.of("tranzila-other");

  private final TranzilaProvider provider = wired.provider;

  @Test
  void the_provider_is_named_tranzila_and_claims_the_transaction_handshake_and_page_namespaces() {
    assertThat(provider.id().value()).isEqualTo("tranzila");
    assertThat(provider.wireRoutes().namespaces())
        .containsExactly(
            "/v1/transaction",
            "/v2/handshake/create",
            "/tranzila/directng",
            "/tranzila/hf",
            "/tranzila/3ds");
    assertThat(provider.wireRoutes().covers("/v1/transaction/credit_card/create")).isTrue();
    assertThat(provider.wireRoutes().covers("/v1/transactions")).isFalse();
    assertThat(provider.wireRoutes().covers("/v1/payment_intents")).isFalse();
    assertThat(provider.wireRoutes().covers("/v2/handshake/create")).isTrue();
    assertThat(provider.wireRoutes().covers("/v2/handshakes")).isFalse();
    assertThat(provider.wireRoutes().covers("/v2/handshake/payments")).isFalse();
    assertThat(provider.wireRoutes().covers("/v2/m-1/payments")).isFalse();
    assertThat(provider.wireRoutes().covers("/tranzila/directng/myterminal/iframenew.php"))
        .isTrue();
    assertThat(provider.wireRoutes().covers("/tranzila/hf/process/ajax_process_transaction"))
        .isTrue();
    assertThat(provider.wireRoutes().covers("/v1/transaction/credit_card/3ds/complete")).isTrue();
    assertThat(provider.wireRoutes().covers("/tranzila/3ds/challenge/3ds_1")).isTrue();
  }

  @Test
  void an_address_off_its_routes_is_answered_in_the_published_error_schema() {
    ProviderWireAnswer notFound =
        provider.theAnswerOffItsRoutes(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v1/transaction/x");
    ProviderWireAnswer wrongMethod =
        provider.theAnswerOffItsRoutes(
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER, "/v1/transaction/credit_card/create");
    ProviderWireAnswer failure =
        provider.theAnswerOffItsRoutes(
            WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/v1/transaction/x");

    ProviderWireAnswer.InThisProvidersOwnWords notFoundBody =
        (ProviderWireAnswer.InThisProvidersOwnWords) notFound;
    ProviderWireAnswer.InThisProvidersOwnWords wrongMethodBody =
        (ProviderWireAnswer.InThisProvidersOwnWords) wrongMethod;
    assertThat(notFoundBody.httpStatus()).isEqualTo(404);
    assertThat(notFoundBody.contentType()).isEqualTo("application/json");
    assertThat(new String(notFoundBody.body(), StandardCharsets.UTF_8))
        .isEqualTo("{\"error_code\":\"404\",\"message\":\"The specified resource was not found\"}");
    assertThat(wrongMethodBody.httpStatus()).isEqualTo(405);
    assertThat(new String(wrongMethodBody.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"error_code\":\"405\",\"message\":\"This address does not answer this HTTP method\"}");
    assertThat(failure).isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
  }

  @Test
  void a_configuration_keeps_one_terminal_per_account_and_stores_them_when_applied() {
    var plan =
        provider.parse(
            configurationOf(
                account("account-1", "terminalone", "key-1"),
                account("account-2", "terminaltwo", "key-2")));
    plan.store();

    assertThat(plan.configurationKeysItReads()).isEqualTo(TranzilaTerminal.CONFIGURATION_KEYS);
    assertThat(wired.terminals.find(TranzilaWiring.RUN, "account-2"))
        .map(TranzilaTerminal::terminalName)
        .contains("terminaltwo");
  }

  @Test
  void two_accounts_naming_one_terminal_or_one_app_key_are_refused() {
    assertThatThrownBy(
            () ->
                provider.parse(
                    configurationOf(
                        account("account-1", "terminalone", "key-1"),
                        account("account-2", "terminalone", "key-2"))))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[1].configuration.terminalName is already configured for another account in"
                + " this test run");
    assertThatThrownBy(
            () ->
                provider.parse(
                    configurationOf(
                        account("account-1", "terminalone", "key-1"),
                        account("account-2", "terminaltwo", "key-1"))))
        .hasMessage(
            "accounts[1].configuration.appKey is already configured for another account in this"
                + " test run");
  }

  @Test
  void an_account_the_configuration_leaves_in_place_keeps_its_terminal_and_its_app_key() {
    assertThatThrownBy(
            () -> provider.parse(configurationOf(account("account-2", "myterminal", "key-9"))))
        .hasMessageContaining("terminalName is already configured");
    assertThatThrownBy(
            () ->
                provider.parse(
                    configurationOf(account("account-2", "terminaltwo", "key-account-1"))))
        .hasMessageContaining("appKey is already configured");
    assertThat(provider.parse(configurationOf(account("account-1", "myterminal", "key-account-1"))))
        .isNotNull();
  }

  @Test
  void forgetting_an_account_removes_its_terminal() {
    provider.forget(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT).remove();

    assertThat(wired.terminals.find(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT)).isEmpty();
  }

  @Test
  void the_vocabulary_names_every_transaction_type_this_route_serves_by_its_wire_spelling() {
    assertThat(provider.vocabulary().operationNames())
        .containsExactly("3ds", "debit", "verify", "force", "reversal", "credit", "cancel");
    assertThat(provider.vocabulary().failureCodes().admits("004")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("900")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("928")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("929")).isFalse();
    assertThat(provider.vocabulary().failureCodes().admits("930")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("931")).isFalse();
    assertThat(provider.vocabulary().failureCodes().admits("017")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("507")).isFalse();
    assertThat(provider.vocabulary().failureCodes().admits("4")).isFalse();
  }

  @Test
  void the_declarations_a_screen_reads_are_the_tranzila_ones() {
    assertThat(provider.accountFields()).isSameAs(TranzilaTerminal.ACCOUNT_FIELDS);
    assertThat(provider.paymentMethodFields()).isSameAs(TranzilaFixtureCards.PAYMENT_METHOD_FIELDS);
    assertThat(provider.keyMechanisms()).isSameAs(TranzilaKeyMechanisms.MECHANISMS);
    assertThat(provider.pairing()).isSameAs(TranzilaPairing.PAIRING);
    assertThat(provider.pairing().providerBaseUrlWithin("https://sim/"))
        .isEqualTo("https://sim/v1");
    assertThat(provider.objectActions().offers(ObjectAction.CAPTURE)).isTrue();
    assertThat(provider.objectActions().offers(ObjectAction.CANCEL)).isTrue();
    assertThat(provider.objectActions().offers(ObjectAction.REFUND)).isTrue();
    assertThat(provider.originationFields().names())
        .containsExactly(
            TranzilaOrigination.A_DEBIT,
            TranzilaOrigination.A_J5,
            TranzilaOrigination.A_PAGE_PAYMENT);
    assertThat(provider.compatibilityChecks(new CheckJson(), wired.ids)).hasSize(11);
  }

  @Test
  void the_payment_page_is_the_one_object_kept_waiting_and_a_transaction_offers_no_payer_step() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(new HostedPageContract.AnObjectItKeepsWaiting("a payment page", true))));
    assertThat(provider.hostedCompletion(TranzilaWiring.RUN, Long.toString(debit)))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                TranzilaApi.TRANZILA, TranzilaProvider.NO_PAGE_TO_COMPLETE));
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    TranzilaWiring.RUN,
                    Long.toString(debit),
                    PayerOutcome.ABANDONED,
                    null,
                    ObjectOrigin.UI)))
        .isEqualTo(new HostedCompletionOutcome.NotOffered(TranzilaProvider.NO_PAGE_TO_COMPLETE));
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    TranzilaWiring.RUN, "42", PayerOutcome.ABANDONED, null, ObjectOrigin.UI)))
        .isEqualTo(new HostedCompletionOutcome.NoSuchObject());
  }

  @Test
  void a_forced_j5_and_its_force_are_two_objects_each_read_by_its_own_transaction_id() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    TranzilaTransaction held = wired.theLastStored();
    wired.create(TranzilaWire.aForce(j5, String.valueOf(held.token()), "60"));
    long force = wired.theLastStored().transactionId();

    ProviderObjectState theJ5 = provider.state(TranzilaWiring.RUN, Long.toString(j5)).orElseThrow();
    ProviderObjectState theForce =
        provider.state(TranzilaWiring.RUN, Long.toString(force)).orElseThrow();

    assertThat(theJ5.providerObjectId()).isEqualTo(Long.toString(j5));
    assertThat(theJ5.identity().members()).containsExactly(Long.toString(j5));
    assertThat(theJ5.kind()).isEqualTo(TranzilaObjectKinds.A_J5);
    assertThat(theJ5.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(theJ5.nativeStatus()).isEqualTo("verify 5 777");
    assertThat(theJ5.amount().value()).isEqualTo("100");
    assertThat(theJ5.currency()).isEqualTo("ILS");
    assertThat(theJ5.authorizationOpen()).isFalse();
    assertThat(theJ5.capturesAccepted()).isFalse();
    assertThat(theJ5.refundsAccepted()).isTrue();
    assertThat(theJ5.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(theJ5.ledger().operations()).hasSize(2);
    assertThat(theJ5.ledger().operations().get(1).createdObjectId())
        .isEqualTo(Long.toString(force));
    assertThat(theJ5.relatedObjects())
        .containsExactly(
            Map.entry("terminalName", "myterminal"),
            Map.entry("card_mask", "458021xxxxxx4245"),
            Map.entry("auth_number", "1000001"),
            Map.entry("hold_expires_at", "2026-10-20T12:00:00Z"),
            Map.entry("force", Long.toString(force)));
    assertThat(theForce.providerObjectId()).isEqualTo(Long.toString(force));
    assertThat(theForce.kind()).isEqualTo(TranzilaObjectKinds.A_FORCE);
    assertThat(theForce.nativeStatus()).isEqualTo("force 000");
    assertThat(theForce.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(theForce.amount().value()).isEqualTo("60");
    assertThat(theForce.ledger().operations()).isEmpty();
    assertThat(theForce.refundsAccepted()).isFalse();
    assertThat(theForce.capturesAccepted()).isFalse();
    assertThat(theForce.relatedObjects())
        .containsExactly(
            Map.entry("terminalName", "myterminal"),
            Map.entry("card_mask", "458021xxxxxx4245"),
            Map.entry("auth_number", "1000002"),
            Map.entry("reference_txn_id", Long.toString(j5)));
    assertThat(provider.state(TranzilaWiring.RUN, "not-a-number")).isEmpty();
    assertThat(provider.state(TranzilaWiring.RUN, "99")).isEmpty();
  }

  @Test
  void a_live_j5_accepts_a_capture_and_says_its_authorization_is_open() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));

    ProviderObjectState state = provider.state(TranzilaWiring.RUN, Long.toString(j5)).orElseThrow();

    assertThat(state.authorizationOpen()).isTrue();
    assertThat(state.capturesAccepted()).isTrue();
    assertThat(state.refundsAccepted()).isFalse();
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void the_validity_of_a_j5_is_read_on_the_run_clock() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofDays(7)));

    assertThat(
            provider.state(TranzilaWiring.RUN, Long.toString(j5)).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(
            provider
                .theObjectsItHoldsUnder(TranzilaWiring.RUN, List.of(Long.toString(j5)))
                .get(0)
                .normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_debit_is_related_to_its_credit_and_the_credit_to_its_own_cancel() {
    String answered = wired.answered(TranzilaWire.aDebit());
    long debit = TranzilaWire.transactionIdIn(answered);
    String auth = TranzilaWire.textIn(answered, "auth_number");
    wired.create(TranzilaWire.aCredit(debit, auth, TranzilaWire.A_VISA, "1"));
    TranzilaTransaction credit = wired.theLastStored();
    wired.create(TranzilaWire.aCancel(credit.transactionId(), String.valueOf(credit.authNumber())));
    long ofTheCredit = wired.theLastStored().transactionId();

    ProviderObjectState theDebit =
        provider.state(TranzilaWiring.RUN, Long.toString(debit)).orElseThrow();
    ProviderObjectState theCredit =
        provider.state(TranzilaWiring.RUN, credit.transactionIdAsText()).orElseThrow();

    assertThat(theDebit.kind()).isEqualTo(TranzilaObjectKinds.A_DEBIT);
    assertThat(theDebit.relatedObjects())
        .containsEntry("credit", credit.transactionIdAsText())
        .doesNotContainKey("cancel")
        .doesNotContainKey("hold_expires_at")
        .doesNotContainKey("reference_txn_id");
    assertThat(theDebit.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(theDebit.refundsAccepted()).isFalse();
    assertThat(theCredit.kind()).isEqualTo(TranzilaObjectKinds.A_CREDIT);
    assertThat(theCredit.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(theCredit.relatedObjects()).containsEntry("cancel", Long.toString(ofTheCredit));
    assertThat(provider.state(TranzilaWiring.RUN, Long.toString(ofTheCredit)).orElseThrow().kind())
        .isEqualTo(TranzilaObjectKinds.A_CANCEL);
  }

  @Test
  void two_follow_ons_of_one_type_are_named_apart() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    String token = String.valueOf(wired.theLastStored().token());
    wired.create(TranzilaWire.aForce(j5, token, "1000"));
    long declined = wired.theLastStored().transactionId();
    wired.create(TranzilaWire.aForce(j5, token, "10"));
    long approved = wired.theLastStored().transactionId();

    assertThat(provider.state(TranzilaWiring.RUN, Long.toString(j5)).orElseThrow().relatedObjects())
        .containsEntry("force", Long.toString(declined))
        .containsEntry("force 2", Long.toString(approved));
    assertThat(
            provider
                .state(TranzilaWiring.RUN, Long.toString(declined))
                .orElseThrow()
                .normalizedStatus())
        .isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void a_declined_original_is_related_to_no_authorization_number() {
    wired.writes(TranzilaOperations.VERIFY, new ScenarioAction.TerminalFailure("004"));
    long j2 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ2()));

    ProviderObjectState state = provider.state(TranzilaWiring.RUN, Long.toString(j2)).orElseThrow();

    assertThat(state.kind()).isEqualTo(TranzilaObjectKinds.A_J2);
    assertThat(state.relatedObjects()).doesNotContainKey("auth_number");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void the_objects_it_holds_are_answered_for_any_transaction_id_and_ignore_ids_that_are_none() {
    long first = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    long second = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ2()));

    assertThat(
            provider.theObjectsItHoldsUnder(
                TranzilaWiring.RUN, List.of(Long.toString(second), "x", Long.toString(first))))
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactlyInAnyOrder(Long.toString(first), Long.toString(second));
  }

  @Test
  void the_objects_are_listed_newest_first_a_page_at_a_time() {
    long first = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    long second = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ2()));
    long third = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    ProviderObjectPage.Listed page =
        (ProviderObjectPage.Listed)
            provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, null, 2));
    ProviderObjectPage.Listed next =
        (ProviderObjectPage.Listed)
            provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, Long.toString(second), 2));

    assertThat(page.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(Long.toString(third), Long.toString(second));
    assertThat(next.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(Long.toString(first));
    assertThat(provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, "x", 2)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
    assertThat(provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, "99", 2)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void a_capture_from_a_screen_forces_the_j5_for_the_amount_asked() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));

    ObjectActionOutcome outcome = act(j5, ObjectAction.CAPTURE, "40");

    TranzilaTransaction force = wired.theLastStored();
    assertThat(outcome)
        .isEqualTo(
            new ObjectActionOutcome.Applied(Long.toString(j5), force.transactionIdAsText(), null));
    assertThat(force.type()).isEqualTo(TranzilaTxnType.FORCE);
    assertThat(force.amount()).isEqualTo("40");
    assertThat(force.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(force.token()).isEqualTo("t000000000000014245");
  }

  @Test
  void
      a_cancel_from_a_screen_reverses_a_live_j5_cancels_a_debit_and_cancels_the_force_of_a_forced_one() {
    long held = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    long forced = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    act(forced, ObjectAction.CAPTURE, "100");
    long force = wired.theLastStored().transactionId();

    assertThat(act(held, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.REVERSAL);
    assertThat(act(debit, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.CANCEL);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(debit);
    assertThat(act(forced, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(force);
  }

  @Test
  void a_refund_from_a_screen_credits_the_debit_or_the_force() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    long forced = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    act(forced, ObjectAction.CAPTURE, "100");
    long force = wired.theLastStored().transactionId();

    assertThat(act(debit, ObjectAction.REFUND, "5"))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(debit);
    assertThat(wired.theLastStored().amount()).isEqualTo("5");
    assertThat(act(forced, ObjectAction.REFUND, "5"))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(force);
  }

  @Test
  void an_action_on_a_follow_on_acts_on_that_transaction() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    act(j5, ObjectAction.CAPTURE, "100");
    long force = wired.theLastStored().transactionId();

    assertThat(act(force, ObjectAction.REFUND, "5"))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    long credit = wired.theLastStored().transactionId();
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(force);
    assertThat(act(credit, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.CANCEL);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(credit);
    assertThat(act(force, ObjectAction.CAPTURE, "1"))
        .isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class);
  }

  @Test
  void an_action_the_provider_refuses_is_reported_in_its_own_words() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    ObjectActionOutcome outcome = act(debit, ObjectAction.REFUND, "1000");

    assertThat(outcome)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "provider tranzila answered HTTP 200 with error_code=23002 and message=id 4 -"
                    + " sum is larger than the original debit transaction",
                null));
  }

  @Test
  void a_refund_of_a_j5_nothing_forced_is_refused_as_tranzila_refuses_crediting_a_hold() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));

    assertThat(act(j5, ObjectAction.REFUND, "1"))
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "provider tranzila answered HTTP 200 with error_code=20112 and"
                    + " message=An origin transaction index is linked to an uncreditable origin"
                    + " transaction (e.g., rejected or non-existent index) or an attempt to"
                    + " credit a different card than the one originally charged.",
                null));
  }

  @Test
  void an_action_a_rule_answers_instead_is_reported_as_the_rules() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    wired.writes(
        TranzilaOperations.CANCEL,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    assertThat(act(debit, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.AnsweredByAScenarioInstead.class);
  }

  @Test
  void an_action_on_nothing_this_run_holds_or_on_a_terminal_it_no_longer_configures_is_refused() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    assertThat(act(99L, ObjectAction.CANCEL, null))
        .isInstanceOf(ObjectActionOutcome.NoSuchObject.class);
    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    TranzilaWiring.RUN, "x", ObjectAction.CANCEL, null, ObjectOrigin.UI)))
        .isInstanceOf(ObjectActionOutcome.NoSuchObject.class);
    wired.terminals.remove(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT);
    assertThat(act(debit, ObjectAction.CANCEL, null))
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                TranzilaObjectActions.THE_RUN_CONFIGURES_NO_TERMINAL));
  }

  @Test
  void a_debit_started_from_a_screen_charges_the_card_and_leaves_the_debit_listed() {
    OriginationOutcome outcome = originate(TranzilaOrigination.A_DEBIT, "USD");

    assertThat(outcome)
        .isEqualTo(
            new OriginationOutcome.Originated(
                "10000001",
                TranzilaOrigination.DECLARED
                    .wayNamed(TranzilaOrigination.A_DEBIT)
                    .orElseThrow()
                    .whatItLeavesBehind(),
                null));
    TranzilaTransaction stored = wired.theLastStored();
    assertThat(stored.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(stored.amount()).isEqualTo("10.53");
    assertThat(stored.currencyCode()).isEqualTo("USD");
    assertThat(stored.origin()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_j5_started_from_a_screen_holds_the_amount() {
    originate(TranzilaOrigination.A_J5, null);

    TranzilaTransaction stored = wired.theLastStored();
    assertThat(stored.isAJ5()).isTrue();
    assertThat(stored.currencyCode()).isEqualTo("ILS");
  }

  @Test
  void a_start_the_provider_refuses_or_a_rule_answers_or_no_account_holds_is_reported_so() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(TranzilaOrigination.AMOUNT, "abc");
    values.put(TranzilaFields.CARD_NUMBER, TranzilaWire.A_VISA);
    values.put(TranzilaFields.EXPIRE_MONTH, "7");
    values.put(TranzilaFields.EXPIRE_YEAR, "2030");

    assertThat(
            provider.originate(
                OriginationRequest.of(
                    TranzilaWiring.RUN,
                    TranzilaWiring.ACCOUNT,
                    TranzilaOrigination.A_DEBIT,
                    values,
                    ObjectOrigin.UI)))
        .isEqualTo(
            new OriginationOutcome.RefusedByTheProvider(
                "provider tranzila answered HTTP 400 with error_code=20004 and message=JSON Schema"
                    + " mismatch: items.unit_price is required",
                null));
    assertThat(
            provider.originate(
                OriginationRequest.of(
                    TranzilaWiring.RUN,
                    "nobody",
                    TranzilaOrigination.A_DEBIT,
                    values,
                    ObjectOrigin.UI)))
        .isInstanceOf(OriginationOutcome.NoSuchAccount.class);
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));
    assertThat(originate(TranzilaOrigination.A_DEBIT, null))
        .isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
  }

  @Test
  void a_start_with_no_value_filled_in_is_refused_by_the_schema_and_not_guessed() {
    assertThat(
            provider.originate(
                OriginationRequest.of(
                    TranzilaWiring.RUN,
                    TranzilaWiring.ACCOUNT,
                    TranzilaOrigination.A_DEBIT,
                    Map.of(),
                    ObjectOrigin.UI)))
        .isInstanceOf(OriginationOutcome.RefusedByTheProvider.class);
  }

  @Test
  void resetting_a_run_clears_what_it_holds_and_resetting_everything_clears_every_run() {
    wired.nonces.claim(OTHER_RUN, "a", "n", TranzilaWiring.NOW, TranzilaWiring.NOW);
    wired.answered(TranzilaWire.aDebit());

    ResetOutcome ofTheRun = provider.reset(TranzilaWiring.RUN, ResetScope.TEST_RUN);
    wired.answered(TranzilaWire.aDebit());
    ResetOutcome ofEverything = provider.reset(TranzilaWiring.RUN, ResetScope.ALL);

    assertThat(ofTheRun.clearedObjects()).isEqualTo(1);
    assertThat(ofEverything.clearedObjects()).isEqualTo(2);
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.held.theRunsItWasAskedToCancel()).containsExactly(TranzilaWiring.RUN);
    assertThat(wired.held.howOftenItWasAskedToCancelEverything()).isEqualTo(1);
  }

  @Test
  void resetting_counts_nonces_exchanges_fixtures_rules_and_trail_rows() {
    wired.configuring(Map.of());
    wired.fixtures.seed(
        fixture(PaymentMethodKind.CARD, card("visa"), "abcd12344245"),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    wired.nonces.claim(TranzilaWiring.RUN, "a", "n", TranzilaWiring.NOW, TranzilaWiring.NOW);
    wired.answered(
        TranzilaWire.aDebit()
            .replace(
                "}]}", "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"d\"}]}"));
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.FixedSuccess());
    wired.audit.record(
        new AuditRecord(
            "aud_1",
            TranzilaWiring.RUN,
            null,
            TranzilaApi.TRANZILA,
            null,
            ObjectOrigin.PROVIDER_API,
            AuditDirection.INBOUND_REQUEST,
            null,
            "POST",
            "/",
            null,
            null,
            null,
            null,
            1,
            TranzilaWiring.NOW,
            Map.of(),
            Map.of(),
            null));

    ResetOutcome outcome = provider.reset(TranzilaWiring.RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(5);
    assertThat(outcome.clearedAuditRecords()).isEqualTo(1);
  }

  @Test
  void
      resetting_everything_counts_the_nonces_exchanges_fixtures_rules_and_trail_rows_of_every_run() {
    wired.configuring(Map.of());
    wired.fixtures.seed(
        fixture(PaymentMethodKind.CARD, card("visa"), "abcd12344245"),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    wired.nonces.claim(OTHER_RUN, "a", "n", TranzilaWiring.NOW, TranzilaWiring.NOW);
    wired.answered(
        TranzilaWire.aDebit()
            .replace(
                "}]}", "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"d\"}]}"));
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.FixedSuccess());
    wired.answered(TranzilaWire.aDebit());

    ResetOutcome outcome = provider.reset(TranzilaWiring.RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(6);
    assertThat(outcome.clearedAuditRecords()).isZero();
    assertThat(wired.held.howOftenItWasAskedToCancelEverything()).isEqualTo(1);
    assertThat(wired.held.theRunsItWasAskedToCancel()).isEmpty();
  }

  @Test
  void an_account_is_disclosed_from_the_terminal_its_run_configures() {
    assertThat(provider.disclosureOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT))
        .hasValueSatisfying(
            disclosed -> {
              assertThat(disclosed.accountId()).isEqualTo("account-1");
              assertThat(disclosed.disclosed().get("terminalName")).containsExactly("myterminal");
            });
    assertThat(provider.disclosureOf(OTHER_RUN, TranzilaWiring.ACCOUNT)).isEmpty();
  }

  @Test
  void the_keyed_exchanges_are_the_ones_create_observed() {
    wired.answered(
        TranzilaWire.aDebit()
            .replace(
                "}]}", "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"k\"}]}"));

    assertThat(
            provider
                .keyedExchanges(
                    new KeyedExchangeQuery(
                        TranzilaWiring.RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 10))
                .exchanges())
        .hasSize(1);
  }

  @Test
  void a_fixture_is_a_card_named_by_a_token_with_a_letter_and_a_brand_of_the_cardtype_table() {
    assertThat(provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), "abcd12344245")))
        .isNotNull();
    assertThatThrownBy(
            () -> provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), "45802100")))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    assertThatThrownBy(() -> provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), "abc")))
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    assertThatThrownBy(
            () -> provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), "a".repeat(20))))
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    assertThatThrownBy(
            () -> provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), "abc-1234")))
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    assertThatThrownBy(() -> provider.seed(fixture(PaymentMethodKind.CARD, card("visa"), null)))
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    assertThatThrownBy(
            () -> provider.seed(fixture(PaymentMethodKind.CARD, card("discover"), "abcd12344245")))
        .hasMessageContaining(TranzilaFixtureCards.A_CARD_TYPE_IS_PUBLISHED);
    assertThatThrownBy(() -> provider.seed(fixture(PaymentMethodKind.WALLET, null, "abcd12344245")))
        .hasMessageContaining(TranzilaFixtureCards.A_STORED_CARD_IS_A_CARD);
    provider.forgetFixture(null).remove();
  }

  @Test
  void every_core_preset_expands_into_rules_on_the_wire_operations_or_says_why_it_cannot() {
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      assertThat(provider.expand(request(preset))).isNotNull();
    }
    assertThat(operationsOf(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS))
        .containsExactly("verify", "force");
    assertThat(operationsOf(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE))
        .containsExactly("verify", "force");
    assertThat(
            ((PresetExpansion.Rules)
                    provider.expand(request(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE)))
                .rules()
                .get(1)
                .action())
        .isEqualTo(new ScenarioAction.TerminalFailure("004"));
    assertThat(operationsOf(ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST))
        .containsExactly("force");
    assertThat(operationsOf(ScenarioPreset.CANCEL_HOLD_SUCCESS)).containsExactly("reversal");
    assertThat(operationsOf(ScenarioPreset.REFUND_FULL_SUCCESS)).containsExactly("credit");
    assertThat(operationsOf(ScenarioPreset.REFUND_PARTIAL_THEN_FULL)).containsExactly("credit");
    assertThat(operationsOf(ScenarioPreset.CARD_ADD_SUCCESS)).containsExactly("verify");
    assertThat(operationsOf(ScenarioPreset.CARD_ADD_FAILED)).containsExactly("verify");
    assertThat(operationsOf(ScenarioPreset.PSP_5XX_THEN_SUCCESS)).hasSize(14);
    for (ScenarioPreset unexpressible :
        List.of(
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS,
            ScenarioPreset.CARD_ADD_CALLBACK_DELAYED,
            ScenarioPreset.WEBHOOK_DELAYED,
            ScenarioPreset.WEBHOOK_DUPLICATE,
            ScenarioPreset.WEBHOOK_OUT_OF_ORDER,
            ScenarioPreset.WEBHOOK_INVALID_SIGNATURE,
            ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_SUCCESS,
            ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_FAILED)) {
      assertThat(provider.expand(request(unexpressible)))
          .isInstanceOf(PresetExpansion.NotExpressible.class);
    }
  }

  @Test
  void every_rule_a_preset_writes_is_one_the_vocabulary_admits() {
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      if (provider.expand(request(preset)) instanceof PresetExpansion.Rules rules) {
        for (var rule : rules.rules()) {
          assertThat(provider.vocabulary().refusals(rule)).as(preset.publishedName()).isEmpty();
          assertThat(rule.provider()).isEqualTo(TranzilaApi.TRANZILA);
          assertThat(rule.testRunId()).isEqualTo(TranzilaWiring.RUN);
          assertThat(rule.priority()).isEqualTo(3);
        }
      }
    }
  }

  @Test
  void every_persona_seeds_a_visa_token_ending_in_its_last_four() {
    var personas = provider.personas(wired.ids);

    assertThat(personas.theNamesItPublishes())
        .containsExactly(
            "tranzila-token-that-is-forced",
            "tranzila-token-whose-force-is-declined",
            "tranzila-token-whose-force-answer-is-lost");
    var fixture = personas.named("tranzila-token-that-is-forced").orElseThrow().fixtures().get(0);
    String token = fixture.mint().get("token");
    assertThat(token).isEqualTo("t000000000000014245");
    assertThat(TranzilaFixtureCards.isAToken(token)).isTrue();
    assertThat(TranzilaCard.cardTypeNamed(Objects.requireNonNull(fixture.card()).brand()))
        .contains(2);
  }

  private List<String> operationsOf(ScenarioPreset preset) {
    return ((PresetExpansion.Rules) provider.expand(request(preset)))
        .rules().stream().map(rule -> String.valueOf(rule.match().operation())).toList();
  }

  private static PresetRequest request(ScenarioPreset preset) {
    return new PresetRequest(preset, TranzilaWiring.RUN, 3, ScenarioMatch.anyRequest());
  }

  private ObjectActionOutcome act(long id, ObjectAction action, String amount) {
    return provider.act(
        ObjectActionRequest.of(
            TranzilaWiring.RUN, Long.toString(id), action, amount, ObjectOrigin.UI));
  }

  private OriginationOutcome originate(String way, String currency) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(TranzilaOrigination.AMOUNT, "10.53");
    if (currency != null) {
      values.put(TranzilaFields.TXN_CURRENCY_CODE, currency);
    }
    values.put(TranzilaFields.CARD_NUMBER, TranzilaWire.A_VISA);
    values.put(TranzilaFields.EXPIRE_MONTH, "7");
    values.put(TranzilaFields.EXPIRE_YEAR, "2030");
    return provider.originate(
        OriginationRequest.of(
            TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, way, values, ObjectOrigin.UI));
  }

  private static FixtureCard card(String brand) {
    return new FixtureCard(brand, "4245", 7, 2030, "credit");
  }

  private static NewPaymentMethodFixture fixture(
      PaymentMethodKind kind, FixtureCard card, String token) {
    Map<String, String> ids = new LinkedHashMap<>();
    if (token != null) {
      ids.put("token", token);
    }
    return new NewPaymentMethodFixture(
        TranzilaApi.TRANZILA,
        TranzilaWiring.RUN,
        TranzilaWiring.ACCOUNT,
        "owner",
        kind,
        "alias",
        card,
        ids,
        null,
        ObjectOrigin.CONTROL_API);
  }

  private static ProviderConfiguration configurationOf(ConfiguredAccount... accounts) {
    return new ProviderConfiguration(
        TranzilaWiring.RUN, List.of(accounts), List.of(), List.of(), Map.of());
  }

  private static ConfiguredAccount account(String accountId, String terminal, String appKey) {
    return new ConfiguredAccount(
        accountId,
        Map.of(
            TranzilaTerminal.TERMINAL_NAME_PROPERTY, terminal,
            TranzilaTerminal.APP_KEY_PROPERTY, appKey,
            TranzilaTerminal.APP_SECRET_PROPERTY, "s"));
  }
}
