package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class OriginationControlTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("origination-control");

  private static final String A_HOLD = "aHold";

  private static final String A_SAVED_CARD_CHARGE = "aSavedCardCharge";

  private static final AValueAFieldOffers THE_CARD_THIS_RUN_SAVED =
      new AValueAFieldOffers("tok_1", "the card Dana Levi saved on obj_1");

  private static final ThePayerStep A_PAGE_THIS_BUILD_SERVES =
      new ThePayerStep.APageThisSimulatorServes("/aprovider/payer/{session}");

  private static final String CONFIGURED_TARGET = "https://merchant.example/callbacks";

  private static final AFieldATesterFillsIn SUM =
      AFieldATesterFillsIn.typed(
          "sum", "amount", true, "what this provider puts on the payer's card.", "10.00");

  private static final AFieldATesterFillsIn DESCRIPTION =
      AFieldATesterFillsIn.typed(
          "description", "what the payer is paying for", false, "the line the payer reads.", null);

  private static final AFieldATesterFillsIn SAVE_CARD =
      AFieldATesterFillsIn.chosenFrom(
          "saveCardToken",
          "keep the payer's card",
          false,
          "whether the card is kept for a later charge.",
          List.of(new AValueAFieldOffers("1", "yes, keep it"), new AValueAFieldOffers("0", "no")));

  private static final AFieldATesterFillsIn NOTIFY_URL =
      AFieldATesterFillsIn.theCallbackTarget(
          "notifyUrl", "where the callback goes", "where this provider posts the callback.");

  private static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          APROVIDER,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Hold the money and settle it later",
                  "a hold the Payments screen lists",
                  A_PAGE_THIS_BUILD_SERVES,
                  List.of(SUM, DESCRIPTION, SAVE_CARD, NOTIFY_URL))),
          new LinkedHashMap<>(
              Map.of("userId", "taken from the account this payment is started on")));

  private static final AFieldATesterFillsIn SAVED_CARD_TO_CHARGE =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          "cardToken",
          "the saved card to charge",
          true,
          "the card one of this run's own payments saved.");

  private static final ProviderOriginationFields A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED =
      new ProviderOriginationFields(
          APROVIDER,
          List.of(
              new DeclaredOrigination(
                  A_SAVED_CARD_CHARGE,
                  "Charge a card this run already saved",
                  "money taken off a card this run already saved",
                  new ThePayerStep.NoPageThisSimulatorServes("no payer is part of this call."),
                  List.of(SAVED_CARD_TO_CHARGE, SUM))),
          new LinkedHashMap<>());

  private static final String A_HOLD_THAT_READS_MANY_FIELDS = "aHoldThatReadsManyFields";

  private static final List<AFieldATesterFillsIn> THE_FIELDS_A_WIDE_WAY_DECLARES =
      List.of(
          aFieldAWideWayReads("sum"),
          aFieldAWideWayReads("description"),
          aFieldAWideWayReads("successUrl"),
          aFieldAWideWayReads("cancelUrl"),
          aFieldAWideWayReads("fullName"),
          aFieldAWideWayReads("phone"),
          aFieldAWideWayReads("email"),
          aFieldAWideWayReads("merchantReference"),
          aFieldAWideWayReads("installments"));

  private static final ProviderOriginationFields A_PROVIDER_THAT_READS_MANY_FIELDS =
      new ProviderOriginationFields(
          APROVIDER,
          List.of(
              new DeclaredOrigination(
                  A_HOLD_THAT_READS_MANY_FIELDS,
                  "Hold the money on a request that carries a great many fields",
                  "a hold the Payments screen lists",
                  A_PAGE_THIS_BUILD_SERVES,
                  THE_FIELDS_A_WIDE_WAY_DECLARES)),
          new LinkedHashMap<>());

  private static AFieldATesterFillsIn aFieldAWideWayReads(String key) {
    return AFieldATesterFillsIn.typed(
        key, key, false, "one of the many values this way of starting a payment reads.", null);
  }

  @Test
  void the_values_a_payment_carries_are_in_the_order_the_way_declares_its_fields() {
    List<String> theOrderTheWayDeclaresThem =
        A_PROVIDER_THAT_READS_MANY_FIELDS
            .wayNamed(A_HOLD_THAT_READS_MANY_FIELDS)
            .orElseThrow()
            .keys();
    Map<String, String> submittedBackToFront = new LinkedHashMap<>();
    for (String key : theOrderTheWayDeclaresThem.reversed()) {
      submittedBackToFront.put(key, "what a tester typed into " + key);
    }

    OriginationRequest request =
        OriginationControl.asked(
            A_PROVIDER_THAT_READS_MANY_FIELDS,
            RUN,
            "merchant-1",
            A_HOLD_THAT_READS_MANY_FIELDS,
            submittedBackToFront,
            List.of(CONFIGURED_TARGET),
            ObjectOrigin.UI);

    assertThat(request.values().keySet())
        .as(
            "a provider reads these onto its own wire by iterating them, so the order is the one"
                + " the way declared its fields in and never the order a browser happened to send"
                + " them in or a hash map happened to hold them in: these %d fields were"
                + " submitted back to front, and far too many orders exist for a pass here to be"
                + " a coincidence",
            theOrderTheWayDeclaresThem.size())
        .containsExactlyElementsOf(theOrderTheWayDeclaresThem);
    assertThat(request.values())
        .as("the order a way declares is carried by the type the request hands every caller")
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void only_the_fields_that_say_the_run_produced_their_values_are_asked_what_the_run_produced() {
    List<String> asked = new ArrayList<>();

    Map<String, List<AValueAFieldOffers>> produced =
        OriginationControl.theValuesThisTestRunHasAlreadyProduced(
            A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED,
            "merchant-1",
            A_SAVED_CARD_CHARGE,
            (account, key) -> {
              asked.add(account + " " + key);
              return List.of(THE_CARD_THIS_RUN_SAVED);
            });

    assertThat(produced).containsExactly(Map.entry("cardToken", List.of(THE_CARD_THIS_RUN_SAVED)));
    assertThat(asked)
        .as("a field whose values the provider declares is never asked what the run produced")
        .containsExactly("merchant-1 cardToken");
  }

  @Test
  void the_account_a_payment_is_started_on_is_the_account_the_run_is_asked_about() {
    List<String> asked = new ArrayList<>();

    OriginationControl.theValuesThisTestRunHasAlreadyProduced(
        A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED,
        "  merchant-2  ",
        "  " + A_SAVED_CARD_CHARGE + "  ",
        (account, key) -> {
          asked.add(account);
          return List.of();
        });

    assertThat(asked).containsExactly("merchant-2");
  }

  @Test
  void a_wizard_that_has_named_no_account_or_no_way_asks_the_run_for_nothing() {
    List<String> asked = new ArrayList<>();
    TheValuesARunHasAlreadyProduced counted =
        (account, key) -> {
          asked.add(key);
          return List.of();
        };

    assertThat(
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED, "  ", A_SAVED_CARD_CHARGE, counted))
        .isEmpty();
    assertThat(
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED, null, A_SAVED_CARD_CHARGE, counted))
        .isEmpty();
    assertThat(
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED, "merchant-1", null, counted))
        .isEmpty();
    assertThat(
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED, "merchant-1", "aWayNobodyDeclared", counted))
        .isEmpty();
    assertThat(asked)
        .as("nothing is read out of a run before a wizard has named the account to read it for")
        .isEmpty();
  }

  @Test
  void a_way_whose_fields_the_provider_declares_the_values_of_asks_the_run_for_nothing() {
    List<String> asked = new ArrayList<>();

    Map<String, List<AValueAFieldOffers>> produced =
        OriginationControl.theValuesThisTestRunHasAlreadyProduced(
            DECLARED,
            "merchant-1",
            A_HOLD,
            (account, key) -> {
              asked.add(key);
              return List.of(THE_CARD_THIS_RUN_SAVED);
            });

    assertThat(produced).isEmpty();
    assertThat(asked).isEmpty();
  }

  @Test
  void a_payment_that_names_no_account_is_refused_before_any_provider_is_asked_anything() {
    Map<String, String> submitted = Map.of("sum", "10.00");

    assertThatThrownBy(() -> asked("", A_HOLD, submitted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a payment with no account named is a payment nothing could sign");
  }

  @Test
  void a_way_of_starting_a_payment_this_provider_never_declared_is_refused_by_the_ones_it_did() {
    Map<String, String> submitted = Map.of("sum", "10.00");

    assertThatThrownBy(() -> asked("merchant-1", "aRefund", submitted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "provider aprovider starts a payment one of these ways and no other: Hold the money"
                + " and settle it later (aHold). Go back a step and pick one of them.");
  }

  @Test
  void a_required_field_left_empty_names_the_field_and_says_what_the_provider_does_with_it() {
    Map<String, String> without = new LinkedHashMap<>();
    without.put("sum", "   ");
    without.put("description", "one seat");

    assertThatThrownBy(() -> asked("merchant-1", A_HOLD, without))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Hold the money and settle it later cannot start without amount: what this provider"
                + " puts on the payer's card. Fill it in and start the payment again.");
  }

  @Test
  void a_field_the_provider_does_not_require_may_be_left_empty_and_is_simply_not_sent() {
    OriginationRequest request = asked("merchant-1", A_HOLD, Map.of("sum", "10.00"));

    assertThat(request.values()).containsExactly(Map.entry("sum", "10.00"));
    assertThat(request.value("description")).isEmpty();
  }

  @Test
  void a_value_this_provider_never_publishes_travels_on_for_this_provider_to_refuse_itself() {
    Map<String, String> invented = new LinkedHashMap<>();
    invented.put("sum", "10.00");
    invented.put("saveCardToken", "maybe");

    OriginationRequest request = asked("merchant-1", A_HOLD, invented);

    assertThat(request.value("saveCardToken"))
        .as(
            "the values a provider declares are the ones it publishes, which a wizard offers and"
                + " never imposes: stripe refuses an unpublished confirm and worldline an"
                + " unpublished authorizationMode in words of their own, and a payment this core"
                + " refused first would hide both from the screen they were written for")
        .contains("maybe");
  }

  @Test
  void a_value_this_provider_publishes_is_taken_and_reaches_the_provider_as_it_was_picked() {
    Map<String, String> picked = new LinkedHashMap<>();
    picked.put("sum", "10.00");
    picked.put("saveCardToken", "1");

    assertThat(asked("merchant-1", A_HOLD, picked).value("saveCardToken")).contains("1");
  }

  @Test
  void the_only_value_this_core_refuses_is_the_one_this_simulator_itself_consumes() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "10.00");
    typed.put("saveCardToken", "a value this provider publishes nowhere");
    typed.put("notifyUrl", CONFIGURED_TARGET);

    assertThatCode(() -> asked("merchant-1", A_HOLD, typed))
        .as(
            "every value but the callback target is forwarded to the provider, which is the party"
                + " that reads it and the party whose refusal a tester came to read, so this core"
                + " refuses none of them however unpublished they are")
        .doesNotThrowAnyException();
  }

  @Test
  void a_callback_target_a_run_with_none_configured_cannot_have_is_refused_with_what_to_do() {
    Map<String, String> stray = new LinkedHashMap<>();
    stray.put("sum", "10.00");
    stray.put("notifyUrl", "https://elsewhere.example/x");

    assertThatThrownBy(() -> asked("merchant-1", A_HOLD, stray, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "where the callback goes must be one of the callback targets this test run configures,"
                + " and this test run configures none. Configure one on the Configuration screen,"
                + " under the account this payment is started on, and start the payment again."
                + OriginationControl.THIS_SIMULATOR_IS_THE_PARTY_THAT_POSTS_A_CALLBACK);
  }

  @Test
  void a_callback_target_a_run_does_not_configure_is_refused_with_the_ones_it_does() {
    Map<String, String> stray = new LinkedHashMap<>();
    stray.put("sum", "10.00");
    stray.put("notifyUrl", "https://elsewhere.example/x");

    assertThatThrownBy(() -> asked("merchant-1", A_HOLD, stray))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "where the callback goes must be one of the callback targets this test run configures,"
                + " and this test run configures "
                + CONFIGURED_TARGET
                + ". Pick one of them, or add this one on the Configuration screen under the"
                + " account this payment is started on."
                + OriginationControl.THIS_SIMULATOR_IS_THE_PARTY_THAT_POSTS_A_CALLBACK);
  }

  @Test
  void a_callback_target_this_run_configures_is_taken_and_travels_with_the_payment() {
    Map<String, String> named = new LinkedHashMap<>();
    named.put("sum", "10.00");
    named.put("notifyUrl", CONFIGURED_TARGET);

    assertThat(asked("merchant-1", A_HOLD, named).value("notifyUrl")).contains(CONFIGURED_TARGET);
  }

  @Test
  void a_value_sent_for_a_field_this_way_of_starting_a_payment_never_reads_is_refused_by_name() {
    Map<String, String> smuggled = new LinkedHashMap<>();
    smuggled.put("sum", "10.00");
    smuggled.put("userId", "u-1");

    assertThatThrownBy(() -> asked("merchant-1", A_HOLD, smuggled))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "nothing is sent for userId, because Hold the money and settle it later reads sum,"
                + " description, saveCardToken, notifyUrl and nothing else. A value typed into a"
                + " field that is not there would be dropped without a word, which is why it is"
                + " refused here instead.");
  }

  @Test
  void a_payment_the_core_admitted_carries_the_way_the_surface_and_the_values_as_typed() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "  10.00  ");
    typed.put("description", "one seat");

    OriginationRequest request = asked("  merchant-1  ", "  " + A_HOLD + "  ", typed);

    assertThat(request.accountId()).isEqualTo("merchant-1");
    assertThat(request.startedAs()).isEqualTo(A_HOLD);
    assertThat(request.askedBy()).isEqualTo(ObjectOrigin.UI);
    assertThat(request.testRunId()).isEqualTo(RUN);
    assertThat(request.required("sum")).isEqualTo("10.00");
  }

  private static OriginationRequest asked(
      String accountId, String startedAs, Map<String, String> submitted) {
    return asked(accountId, startedAs, submitted, List.of(CONFIGURED_TARGET));
  }

  private static OriginationRequest asked(
      String accountId,
      String startedAs,
      Map<String, String> submitted,
      List<String> callbackTargets) {
    return OriginationControl.asked(
        DECLARED, RUN, accountId, startedAs, submitted, callbackTargets, ObjectOrigin.UI);
  }
}
