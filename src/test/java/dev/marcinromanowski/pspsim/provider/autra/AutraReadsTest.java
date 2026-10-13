package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class AutraReadsTest {

  private static final TestRunId RUN = TestRunId.of("autra-reads");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String THE_MOMENT_A_LISTING_THAT_NAMES_NO_END_STOPS_BEFORE =
      "2026-09-22T10:00:01Z";
  private static final String THE_MOMENT_A_LISTING_THAT_NAMES_NO_START_BEGINS_AT =
      "2026-09-15T10:00:01Z";
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String ANOTHER_DOCUMENT = "12345678000177";
  private static final String CHARGED = "500.00";
  private static final String RESERVED = "640.00";
  private static final String AN_ORDER = "pedido-abc-123";
  private static final String A_UUID_NO_PAYMENT_ANSWERS_TO = "550e8400-e29b-41d4-a716-446655440000";
  private static final String NOT_SHAPED_LIKE_A_UUID = "the-one-i-charged";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraMerchant merchant = AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of());
  private final LookUpPayment lookUpPayment = new LookUpPayment(payments);
  private final ListPayments listPayments = new ListPayments(payments);
  private final AutraApi api = apiFor();

  private AutraApi apiFor() {
    merchants.with(merchant);
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private String aSaleOf(String amount, String orderId) {
    return String.valueOf(
        AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, orderId).providerObjectId());
  }

  private String aReservationOf(String amount) {
    return String.valueOf(
        AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null).providerObjectId());
  }

  private ReadOutcome theAnswerTo(String readNamed, Map<String, String> submitted) {
    return AutraReads.theAnswerTo(
        ReadControl.asked(AutraReads.DECLARED, RUN, ACCOUNT, readNamed, submitted, ObjectOrigin.UI),
        merchant,
        lookUpPayment,
        listPayments,
        NOW);
  }

  private ReadOutcome theAnswerToTheLookUpOf(String paymentId) {
    return theAnswerTo(
        AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS,
        Map.of(AutraFields.PAYMENT_ID_LOWERCASE_D, paymentId));
  }

  private ReadOutcome theAnswerToTheListing(String... narrowedBy) {
    Map<String, String> submitted = new LinkedHashMap<>();
    for (int named = 0; named < narrowedBy.length; named += 2) {
      submitted.put(narrowedBy[named], narrowedBy[named + 1]);
    }
    return theAnswerTo(AutraReads.THE_PAYMENTS_OF_ONE_ESTABLISHMENT, submitted);
  }

  private static List<AnsweredRow> theRowsOf(ReadOutcome outcome) {
    return ((ReadOutcome.Answered) outcome).rows();
  }

  private static Map<String, String> theFactsOf(AnsweredRow row) {
    return theFactsAnsweredAsOneValueIn(row.facts());
  }

  private static Map<String, String> theFactsAnsweredAsOneValueIn(List<Fact> facts) {
    Map<String, String> answered = new LinkedHashMap<>();
    for (Fact fact : facts) {
      if (fact.value() instanceof AnsweredValue.TheValueItAnswered one) {
        answered.put(fact.asTheProviderNamesIt(), one.asTheProviderAnsweredIt());
      }
    }
    return answered;
  }

  private static AnsweredValue theMemberNamed(List<Fact> facts, String name) {
    for (Fact fact : facts) {
      if (fact.asTheProviderNamesIt().equals(name)) {
        return fact.value();
      }
    }
    throw new AssertionError("provider autra answered no member named " + name);
  }

  private static List<Fact> theWholeQueryAnswerOf(ReadOutcome outcome) {
    return ((ReadOutcome.Answered) outcome).whatItAnsweredAboutTheQuery();
  }

  private static String theWordsOf(ReadOutcome outcome) {
    return ((ReadOutcome.RefusedByTheProvider) outcome).inItsOwnWords();
  }

  private static DeclaredRead theReadNamed(String name) {
    return AutraReads.DECLARED.readNamed(name).orElseThrow();
  }

  @Test
  void the_questions_autra_publishes_are_its_two_read_only_addresses_and_no_invention() {
    assertThat(AutraReads.DECLARED.names())
        .as(
            "a read is named by the operation the provider publishes it as, and autra names an"
                + " operation by its method and path")
        .containsExactly("GET /v1/acquiring/payments/{paymentId}", "GET /v1/acquiring/payments");
    assertThat(AutraReads.DECLARED.reads())
        .extracting(DeclaredRead::label)
        .containsExactly(
            "Read one payment back with every step it has been through",
            "Find the payments one establishment took in a window of dates");
  }

  @Test
  void no_question_autra_publishes_is_an_operation_a_scenario_rule_of_a_test_run_may_name() {
    assertThat(AutraOperations.VOCABULARY.operationNames())
        .as(
            "the Look a payment up screen tells a tester that asking a question twice leaves"
                + " their test run exactly as it was whenever no rule may be matched on it, and"
                + " that sentence is true of autra only while autra keeps its reads out of the"
                + " operations a rule may name: an operation a rule may name is one a rule"
                + " carrying a story moves that story on for")
        .doesNotContainAnyElementsOf(AutraReads.DECLARED.names());
  }

  @Test
  void each_question_asks_for_the_query_parameters_autra_publishes_and_never_for_the_document() {
    assertThat(theReadNamed(AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS).keys())
        .as("autra reads the identifier back as paymentId in the path of this route")
        .containsExactly("paymentId");
    assertThat(theReadNamed(AutraReads.THE_PAYMENTS_OF_ONE_ESTABLISHMENT).keys())
        .as(
            "every value the listing asks for is one of autra's own query parameters, and the"
                + " document is the account's and never typed")
        .containsExactly("from", "to", "status", "type", "page", "limit")
        .doesNotContain(AutraFields.DOCUMENT_ID);
  }

  @Test
  void both_questions_declare_the_document_header_as_what_the_simulator_fills_in_for_a_tester() {
    for (DeclaredRead published : AutraReads.DECLARED.reads()) {
      assertThat(published.fieldsItFillsInItself())
          .as(published.name())
          .containsExactly(
              entry(
                  AutraFields.MERCHANT_DOCUMENT_HEADER,
                  AutraReads.THE_ESTABLISHMENT_IS_THE_ACCOUNTS_OWN));
      assertThat(published.theQuestionItAnswers())
          .as(
              "the sentence a tester reads to pick between questions says what this one answers,"
                  + " and where the establishment comes from is a declaration of its own")
          .doesNotContain(AutraFields.MERCHANT_DOCUMENT_HEADER);
    }
    assertThat(AutraReads.THE_ESTABLISHMENT_IS_THE_ACCOUNTS_OWN)
        .as(
            "a tester is told which refusals the document reaches on the wire and never from this"
                + " form, because the form sends the account's own document every time")
        .contains(AutraErrors.MISSING_DOCUMENT_ID)
        .contains(AutraErrors.INVALID_DOCUMENT)
        .contains(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void the_payment_is_suggested_out_of_what_this_run_took_and_the_box_still_takes_another() {
    assertThat(theReadNamed(AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS).fields())
        .as(
            "autra reads this identifier itself and answers 400 INVALID_PAYMENT_ID and 404"
                + " PAYMENT_NOT_FOUND for one it holds no payment under, so what this run took is"
                + " suggested on the box and never imposed on it")
        .extracting(
            AFieldATesterFillsIn::required,
            AFieldATesterFillsIn::namesAValueThisTestRunHasAlreadyProduced,
            AFieldATesterFillsIn::thisSimulatorIsTheOneThatConsumesTheValue)
        .containsExactly(tuple(true, true, false));
    assertThat(theReadNamed(AutraReads.THE_PAYMENTS_OF_ONE_ESTABLISHMENT).fields())
        .as(
            "the listing refuses what a tester types with codes of its own, so a value it"
                + " enumerates is still typed and not picked from a list that could not send an"
                + " unpublished one")
        .allSatisfy(
            field -> {
              assertThat(field.required()).as(field.key()).isFalse();
              assertThat(field.choices()).as(field.key()).isEmpty();
              assertThat(field.namesAValueThisTestRunHasAlreadyProduced())
                  .as(field.key())
                  .isFalse();
            });
  }

  @Test
  void a_payment_is_read_back_with_every_member_under_the_name_autra_answers_it_under() {
    String paymentId = aSaleOf(CHARGED, AN_ORDER);

    List<AnsweredRow> rows = theRowsOf(theAnswerToTheLookUpOf(paymentId));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().providerObjectId())
        .as("the row names the payment, so the screen can hand a tester on to its actions")
        .isEqualTo(paymentId);
    assertThat(theFactsOf(rows.getFirst()))
        .containsEntry("paymentID", paymentId)
        .containsEntry("orderId", AN_ORDER)
        .containsEntry("type", AutraTimeline.SALE)
        .containsEntry("status", AutraStatus.ACCEPTED)
        .containsEntry("amount", CHARGED)
        .containsEntry("currency", "BRL")
        .containsEntry("documentId", DOCUMENT)
        .containsEntry("voidRetryable", "true")
        .containsEntry("createdAt", "2026-09-22T10:00:00Z");
  }

  @Test
  void the_timeline_of_a_payment_is_answered_as_the_steps_autra_nests_under_the_name_events() {
    String paymentId = aSaleOf(CHARGED, null);

    List<Fact> answered = theRowsOf(theAnswerToTheLookUpOf(paymentId)).getFirst().facts();

    assertThat(answered)
        .as(
            "every member of a payment is named the way autra answers it, and a name made of a"
                + " member and the place a step was answered in is a name autra answers nothing"
                + " under")
        .extracting(Fact::asTheProviderNamesIt)
        .allSatisfy(named -> assertThat(named).doesNotContain("."));
    AnsweredValue events = theMemberNamed(answered, AutraFields.EVENTS);
    assertThat(events)
        .asInstanceOf(InstanceOfAssertFactories.type(AnsweredValue.TheElementsUnderIt.class))
        .satisfies(
            timeline -> {
              assertThat(timeline.elements()).hasSize(1);
              assertThat(timeline.elements().getFirst())
                  .asInstanceOf(
                      InstanceOfAssertFactories.type(AnsweredValue.TheMembersUnderIt.class))
                  .satisfies(
                      step ->
                          assertThat(theFactsAnsweredAsOneValueIn(step.members()))
                              .as("a step of the timeline is answered as the object autra nests")
                              .containsEntry("type", AutraTimeline.SALE)
                              .containsEntry("status", AutraStatus.ACCEPTED)
                              .containsEntry("amount", CHARGED)
                              .containsEntry("at", "2026-09-22T10:00:00Z"));
            });
  }

  @Test
  void an_identifier_no_payment_of_this_establishment_answers_to_is_refused_in_autras_words() {
    assertThat(theWordsOf(theAnswerToTheLookUpOf(A_UUID_NO_PAYMENT_ANSWERS_TO)))
        .contains(AutraErrors.PAYMENT_NOT_FOUND)
        .contains("inexistente, de outro tenant ou de outro estabelecimento");
  }

  @Test
  void a_payment_of_another_establishment_of_this_run_is_refused_as_one_nothing_holds() {
    String paymentId = aSaleOf(CHARGED, null);
    AutraMerchant elsewhere = AutraDoubles.merchant(RUN, "merchant-2", ANOTHER_DOCUMENT, Map.of());

    ReadOutcome answered =
        AutraReads.theAnswerTo(
            ReadControl.asked(
                AutraReads.DECLARED,
                RUN,
                "merchant-2",
                AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS,
                Map.of(AutraFields.PAYMENT_ID_LOWERCASE_D, paymentId),
                ObjectOrigin.UI),
            elsewhere,
            lookUpPayment,
            listPayments,
            NOW);

    assertThat(theWordsOf(answered))
        .as("autra publishes one refusal for a payment of another establishment and for none")
        .contains(AutraErrors.PAYMENT_NOT_FOUND);
  }

  @Test
  void an_identifier_that_is_not_shaped_like_a_uuid_is_refused_in_autras_words() {
    assertThat(theWordsOf(theAnswerToTheLookUpOf(NOT_SHAPED_LIKE_A_UUID)))
        .contains(AutraErrors.INVALID_PAYMENT_ID)
        .contains("string of format uuid");
  }

  @Test
  void the_listing_answers_the_payments_of_this_establishment_newest_first() {
    String charged = aSaleOf(CHARGED, null);
    String reserved = aReservationOf(RESERVED);

    List<AnsweredRow> rows = theRowsOf(theAnswerToTheListing());

    assertThat(rows)
        .as("every row of the listing is a payment of this establishment and nothing else")
        .extracting(AnsweredRow::providerObjectId)
        .containsExactly(reserved, charged);
  }

  @Test
  void the_window_and_the_page_the_listing_applied_are_answered_about_the_question_it_was_asked() {
    aSaleOf(CHARGED, null);

    List<Fact> aboutTheQuestion = theWholeQueryAnswerOf(theAnswerToTheListing());

    assertThat(theFactsAnsweredAsOneValueIn(aboutTheQuestion))
        .as(
            "a tester who named no window is told which one autra applied, and a tester on a full"
                + " page is told whether another follows it, and neither is a payment of this"
                + " establishment")
        .containsExactly(
            Map.entry("page", "1"),
            Map.entry("limit", "50"),
            Map.entry("hasMore", "false"),
            Map.entry("from", THE_MOMENT_A_LISTING_THAT_NAMES_NO_START_BEGINS_AT),
            Map.entry("to", THE_MOMENT_A_LISTING_THAT_NAMES_NO_END_STOPS_BEFORE));
  }

  @Test
  void the_look_up_answers_the_payment_and_nothing_about_the_question_it_was_asked() {
    String paymentId = aSaleOf(CHARGED, null);

    assertThat(theWholeQueryAnswerOf(theAnswerToTheLookUpOf(paymentId)))
        .as(
            "autra reads the identifier back inside the payment itself, so this address answers"
                + " nothing of its own about the question")
        .isEmpty();
  }

  @Test
  void a_page_that_has_another_behind_it_says_so_rather_than_leaving_a_tester_to_count() {
    aSaleOf(CHARGED, null);
    aSaleOf(CHARGED, null);

    assertThat(
            theFactsAnsweredAsOneValueIn(
                theWholeQueryAnswerOf(theAnswerToTheListing(AutraFields.LIMIT, "1"))))
        .containsEntry("hasMore", "true")
        .containsEntry("limit", "1");
  }

  @Test
  void a_listing_that_matches_nothing_answers_no_row_and_still_says_which_window_it_applied() {
    aSaleOf(CHARGED, null);

    ReadOutcome answered = theAnswerToTheListing(AutraFields.STATUS, AutraStatus.DECLINED);

    assertThat(((ReadOutcome.Answered) answered).itAnsweredNoRowAtAll())
        .as(
            "the sentence that says what an empty listing means is reached by the rows alone, so"
                + " the window autra echoes beside them no longer suppresses it")
        .isTrue();
    assertThat(theFactsAnsweredAsOneValueIn(theWholeQueryAnswerOf(answered)))
        .as(
            "a tester whose listing matched nothing is told which window autra applied, which is"
                + " the first thing to widen when nothing came back")
        .containsEntry("hasMore", "false")
        .containsEntry("from", THE_MOMENT_A_LISTING_THAT_NAMES_NO_START_BEGINS_AT)
        .containsEntry("to", THE_MOMENT_A_LISTING_THAT_NAMES_NO_END_STOPS_BEFORE);
  }

  @Test
  void a_status_autra_does_not_publish_is_refused_by_autra_and_not_by_the_form() {
    assertThat(theWordsOf(theAnswerToTheListing(AutraFields.STATUS, "NOT_A_STATUS")))
        .contains(AutraErrors.INVALID_STATUS)
        .contains("Valor desconhecido");
  }

  @Test
  void a_comma_separated_list_of_statuses_is_one_value_no_list_of_single_values_could_offer() {
    String charged = aSaleOf(CHARGED, null);

    List<AnsweredRow> rows =
        theRowsOf(
            theAnswerToTheListing(
                AutraFields.STATUS, AutraStatus.ACCEPTED + "," + AutraStatus.CAPTURED));

    assertThat(rows).extracting(AnsweredRow::providerObjectId).containsExactly(charged);
  }

  @Test
  void a_type_autra_does_not_enumerate_is_refused_by_autra_and_not_by_the_form() {
    assertThat(theWordsOf(theAnswerToTheListing(AutraFields.TYPE, "REFUND")))
        .contains(AutraErrors.INVALID_TYPE)
        .contains("enumerates SALE and PREAUTH");
  }

  @Test
  void a_window_wider_than_the_maximum_autra_publishes_is_refused_by_autra() {
    assertThat(
            theWordsOf(
                theAnswerToTheListing(
                    AutraFields.FROM, "2026-01-01", AutraFields.TO, "2026-09-22")))
        .contains(AutraErrors.RANGE_TOO_LARGE)
        .contains("maxima: 31");
  }

  @Test
  void a_moment_of_neither_shape_autra_reads_is_refused_by_autra() {
    assertThat(theWordsOf(theAnswerToTheListing(AutraFields.FROM, "last tuesday")))
        .contains(AutraErrors.INVALID_FROM)
        .contains("RFC3339 ou YYYY-MM-DD");
  }

  @Test
  void a_page_or_a_limit_outside_the_bounds_autra_publishes_is_refused_by_autra() {
    assertThat(theWordsOf(theAnswerToTheListing(AutraFields.PAGE, "0")))
        .contains(AutraErrors.INVALID_PAGE);
    assertThat(theWordsOf(theAnswerToTheListing(AutraFields.LIMIT, "201")))
        .contains(AutraErrors.INVALID_LIMIT);
  }

  @Test
  void a_bare_date_covers_the_whole_of_that_day_so_a_window_of_one_day_holds_what_it_took() {
    String charged = aSaleOf(CHARGED, null);

    List<AnsweredRow> rows =
        theRowsOf(
            theAnswerToTheListing(AutraFields.FROM, "2026-09-22", AutraFields.TO, "2026-09-22"));

    assertThat(rows).extracting(AnsweredRow::providerObjectId).containsExactly(charged);
  }

  @Test
  void the_call_a_look_up_is_performed_with_is_signed_with_the_document_of_the_account() {
    AutraApiCall call = AutraReads.theLookUpCallOf(A_UUID_NO_PAYMENT_ANSWERS_TO, merchant);

    assertThat(call.method()).isEqualTo("GET");
    assertThat(call.path()).isEqualTo("/v1/acquiring/payments/" + A_UUID_NO_PAYMENT_ANSWERS_TO);
    assertThat(call.header(AutraFields.MERCHANT_DOCUMENT_HEADER))
        .as(
            "autra calls the header the preferred way of naming the establishment, because it"
                + " keeps the document out of the address and out of an access log")
        .contains(DOCUMENT);
    assertThat(call.queryParameter(AutraFields.DOCUMENT_ID)).isEmpty();
  }

  @Test
  void the_call_a_listing_is_performed_with_carries_what_a_tester_typed_and_nothing_else() {
    AutraApiCall call =
        AutraReads.theListingCallOf(
            ReadControl.asked(
                AutraReads.DECLARED,
                RUN,
                ACCOUNT,
                AutraReads.THE_PAYMENTS_OF_ONE_ESTABLISHMENT,
                Map.of(AutraFields.STATUS, AutraStatus.ACCEPTED, AutraFields.LIMIT, "7"),
                ObjectOrigin.UI),
            merchant);

    assertThat(call.path()).isEqualTo("/v1/acquiring/payments");
    assertThat(call.header(AutraFields.MERCHANT_DOCUMENT_HEADER)).contains(DOCUMENT);
    assertThat(call.query())
        .containsOnlyKeys(AutraFields.STATUS, AutraFields.LIMIT)
        .containsEntry(AutraFields.STATUS, List.of(AutraStatus.ACCEPTED))
        .containsEntry(AutraFields.LIMIT, List.of("7"));
    assertThat(call.namesTheQueryParameter(AutraFields.PAGE))
        .as(
            "a value a tester left empty is a value the call does not name, which is how autra"
                + " tells a default apart from a refusal")
        .isFalse();
  }

  @Test
  void every_member_autra_answers_is_read_as_the_kind_of_answer_autra_gave_it() {
    Map<String, AutraJson> answered = new LinkedHashMap<>();
    answered.put("status", AutraJson.text(AutraStatus.ACCEPTED));
    answered.put("installments", AutraJson.number("1"));
    answered.put("voidRetryable", AutraJson.flag(false));
    answered.put("maskedCard", new AutraJson.JsonNothing());
    answered.put("events", AutraJson.array(List.of()));
    answered.put(
        "acquirerTransaction", AutraJson.object(Map.of("trackingNumber", AutraJson.text("t-1"))));
    answered.put("errors", AutraJson.array(List.of(AutraJson.text("one"), AutraJson.text("two"))));

    List<Fact> facts = AutraReads.theFactsOf(AutraJson.object(answered));

    assertThat(facts)
        .as(
            "a member autra answered empty or with no value at all is a member of the answer and"
                + " never a member this module dropped or renamed")
        .extracting(Fact::asTheProviderNamesIt, Fact::value)
        .containsExactly(
            tuple("status", new AnsweredValue.TheValueItAnswered(AutraStatus.ACCEPTED)),
            tuple("installments", new AnsweredValue.TheValueItAnswered("1")),
            tuple("voidRetryable", new AnsweredValue.TheValueItAnswered("false")),
            tuple("maskedCard", new AnsweredValue.NoValueAtAll()),
            tuple("events", new AnsweredValue.TheElementsUnderIt(List.of())),
            tuple(
                "acquirerTransaction",
                new AnsweredValue.TheMembersUnderIt(List.of(new Fact("trackingNumber", "t-1")))),
            tuple(
                "errors",
                new AnsweredValue.TheElementsUnderIt(
                    List.of(
                        new AnsweredValue.TheValueItAnswered("one"),
                        new AnsweredValue.TheValueItAnswered("two")))));
  }

  @Test
  void an_answer_this_module_did_not_build_is_refused_here_rather_than_printed_as_rows() {
    AutraOperationResult notAnObject =
        AutraOperationResult.succeeded(null, AutraBody.of(200, AutraJson.text("nothing nested")));

    assertThatThrownBy(() -> AutraReads.theOnePaymentItAnswered(notAnObject))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answers both of its read-only addresses with one JSON object");
    assertThatThrownBy(() -> AutraReads.theListingItAnswered(notAnObject))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answers both of its read-only addresses with one JSON object");
  }

  @Test
  void the_window_autra_answered_beside_the_items_is_every_member_but_the_items_themselves() {
    Map<String, AutraJson> answered = new LinkedHashMap<>();
    answered.put("page", AutraJson.number("2"));
    answered.put(AutraFields.ITEMS, AutraJson.array(List.of()));
    answered.put("hasMore", AutraJson.flag(true));

    assertThat(AutraReads.theWindowAndThePageItAnsweredBesideTheItems(AutraJson.object(answered)))
        .as("the payments are the rows of the answer and never a fact about the question too")
        .extracting(Fact::asTheProviderNamesIt)
        .containsExactly("page", "hasMore");
  }

  @Test
  void a_listing_answer_with_no_items_and_an_item_that_is_no_payment_are_both_refused_here() {
    AutraOperationResult noItems =
        AutraOperationResult.succeeded(
            null, AutraBody.of(200, AutraJson.object(Map.of("page", AutraJson.number("1")))));
    AutraOperationResult anItemThatIsNoPayment =
        AutraOperationResult.succeeded(
            null,
            AutraBody.of(
                200,
                AutraJson.object(
                    Map.of(
                        AutraFields.ITEMS,
                        AutraJson.array(List.of(AutraJson.text("a payment")))))));

    assertThatThrownBy(() -> AutraReads.theListingItAnswered(noItems))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("carries no items array at all");
    assertThatThrownBy(() -> AutraReads.theListingItAnswered(anItemThatIsNoPayment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("carries an item that is not one");
  }

  @Test
  void a_payment_answering_under_no_identifier_is_refused_here_rather_than_drawn_unreachable() {
    AutraOperationResult namesNoPayment =
        AutraOperationResult.succeeded(
            null,
            AutraBody.of(200, AutraJson.object(Map.of("status", AutraJson.text("ACCEPTED")))));

    assertThatThrownBy(() -> AutraReads.theOnePaymentItAnswered(namesNoPayment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("names every payment it answers under paymentID");
  }

  @Test
  void every_payment_this_run_took_is_offered_to_be_picked_by_what_tells_them_apart() {
    String charged = aSaleOf(CHARGED, AN_ORDER);
    String reserved = aReservationOf(RESERVED);

    List<AValueAFieldOffers> offered =
        AutraReads.thePaymentsThisTestRunHasAlreadyTaken(payments.ofMerchant(RUN, DOCUMENT));

    assertThat(offered)
        .extracting(AValueAFieldOffers::value)
        .as("the newest payment of the establishment is the one a tester reaches for first")
        .containsExactly(reserved, charged);
    assertThat(offered.getLast().label())
        .isEqualTo(
            "the SALE of "
                + CHARGED
                + " BRL autra took at 2026-09-22T10:00:00Z, which holds ACCEPTED, your order "
                + AN_ORDER);
    assertThat(offered.getFirst().label())
        .as("a payment carrying no order of the merchant's own names none")
        .isEqualTo(
            "the PREAUTH of "
                + RESERVED
                + " BRL autra took at 2026-09-22T10:00:00Z, which holds ACCEPTED");
  }

  @Test
  void nothing_is_offered_for_a_run_that_has_taken_no_payment_on_this_establishment() {
    assertThat(AutraReads.thePaymentsThisTestRunHasAlreadyTaken(payments.ofMerchant(RUN, DOCUMENT)))
        .isEmpty();
  }
}
