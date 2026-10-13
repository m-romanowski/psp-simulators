package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead.EmptyAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAReadLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ReadPageTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final String A_RUN = "a-run";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_RETRACE = "aRetrace";

  private static final String THE_PAIRS_A_SCREEN_DRAWS_A_NAME_AND_A_VALUE_IN = "class=\"pairs\"";

  private static final String A_LISTING = "aListing";

  private static final String STATUS = "status";

  private static final Pattern A_LIST_FOR_A_FIELD =
      Pattern.compile("(?s)<select[^>]*name=\"field\\.[^\"]+\"");

  private static final String AN_APOSTROPHE_AS_THE_TEMPLATE_ENGINE_WRITES_IT = "&#39;";

  private static String asTheEngineWritesIt(String sentence) {
    return sentence.replace("'", AN_APOSTROPHE_AS_THE_TEMPLATE_ENGINE_WRITES_IT);
  }

  private static Pattern theSuggestionsOffered(String key, String source) {
    return Pattern.compile("(?s)<datalist[^>]*id=\"" + key + source + "\"");
  }

  private static final String NOTHING_FOUND =
      "this provider holds no charge of yours under those identifiers.";

  private static final AValueAFieldOffers THE_CARD_THIS_RUN_SAVED =
      new AValueAFieldOffers("tok_1", "the card Dana Levi saved on obj_1");

  private static final ProviderReads DECLARED =
      ProviderReads.theOnesItPublishes(
          APROVIDER,
          List.of(
              new DeclaredRead(
                  A_RETRACE,
                  "Find the charge",
                  "which charge it holds under a merchant's own identifiers.",
                  EmptyAnswer.whatItMeans(NOTHING_FOUND),
                  List.of(
                      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
                          "cardToken", "the saved card", true, "what this run minted."),
                      AFieldATesterFillsIn.typed(
                          "myOwnIdentifier",
                          "your own identifier",
                          true,
                          "what it finds the charge by.",
                          "1001")))));

  private static final ProviderReads A_READ_WHOSE_VALUES_ITS_PROVIDER_PUBLISHES =
      ProviderReads.theOnesItPublishes(
          APROVIDER,
          List.of(
              new DeclaredRead(
                  A_LISTING,
                  "List what this establishment took",
                  "which payments it holds in a window.",
                  EmptyAnswer.whatItMeans(NOTHING_FOUND),
                  List.of(
                      AFieldATesterFillsIn.chosenFrom(
                          STATUS,
                          "the status to keep",
                          false,
                          "which statuses it answers.",
                          List.of(
                              new AValueAFieldOffers("ACCEPTED", "taken and not yet settled"),
                              new AValueAFieldOffers("CAPTURED", "settled")))))));

  @Test
  void the_screen_is_drawn_with_the_questions_this_provider_publishes_and_asks_it_nothing_yet() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();

    String view = aPageOf(module).render(APROVIDER.value(), A_RUN, ACCOUNT, null, Map.of(), model);

    assertThat(view).isEqualTo("reads");
    assertThat(model.getAttribute("path")).isEqualTo("/ui/reads");
    assertThat(model.getAttribute("screen")).isEqualTo("reads");
    assertThat(module.theReadsItWasAskedFor())
        .as("opening the screen asks the provider nothing, because nothing has been filled in")
        .isEmpty();
    assertThat(theFormOn(model).panel()).isEqualTo(ReadForm.Panel.WHICH_QUESTION);
    assertThat(theFormOn(model).answer()).isNull();
  }

  @Test
  void the_question_a_tester_picked_is_drawn_with_the_values_this_run_has_already_minted() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();

    aPageOf(module).render(APROVIDER.value(), A_RUN, ACCOUNT, A_RETRACE, Map.of(), model);

    assertThat(theFormOn(model).fields().get(0).choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice("tok_1", THE_CARD_THIS_RUN_SAVED.label(), false));
  }

  @Test
  void a_read_its_providers_vocabulary_does_not_name_is_drawn_as_one_no_rule_of_this_run_decides() {
    Model namedInTheVocabulary = new ConcurrentModel();
    Model namedNowhere = new ConcurrentModel();

    aPageOf(aModulePublishingTheRetrace().namingInItsVocabulary(A_RETRACE))
        .render(APROVIDER.value(), A_RUN, ACCOUNT, A_RETRACE, Map.of(), namedInTheVocabulary);
    aPageOf(aModulePublishingTheRetrace().namingInItsVocabulary("anOperationThatIsNotThisRead"))
        .render(APROVIDER.value(), A_RUN, ACCOUNT, A_RETRACE, Map.of(), namedNowhere);

    assertThat(
            ((ReadForm.View) namedInTheVocabulary.getAttribute(ReadPage.THE_FORM))
                .aRuleOfThisRunCouldDecideIt())
        .as(
            "a read whose name the provider's own vocabulary carries is an operation a rule of"
                + " this run can be written against, and the screen says so")
        .isTrue();
    assertThat(
            ((ReadForm.View) namedNowhere.getAttribute(ReadPage.THE_FORM))
                .aRuleOfThisRunCouldDecideIt())
        .as(
            "a provider may publish a read outside the operations its vocabulary names, and"
                + " telling a tester their rule is matched on it would send them looking for a"
                + " rule that can never fire")
        .isFalse();
  }

  @Test
  void asking_the_question_hands_the_provider_the_values_a_tester_filled_in_and_draws_its_answer() {
    AnsweredRow row = new AnsweredRow("100003", List.of(new AnsweredRow.Fact("status", "paid")));
    ProviderModuleDouble module =
        aModulePublishingTheRetrace().answeringARead(new ReadOutcome.Answered(List.of(row)));
    Model model = new ConcurrentModel();

    aPageOf(module)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "1001"),
            model);

    assertThat(module.theReadsItWasAskedFor())
        .singleElement()
        .satisfies(
            asked -> {
              assertThat(asked.readNamed()).isEqualTo(A_RETRACE);
              assertThat(asked.accountId()).isEqualTo(ACCOUNT);
              assertThat(asked.values())
                  .containsEntry("cardToken", "tok_1")
                  .containsEntry("myOwnIdentifier", "1001");
              assertThat(asked.askedBy())
                  .as("a read a tester clicked is recorded as one this simulator was asked for")
                  .isEqualTo(ObjectOrigin.UI);
            });
    ReadForm.Answer answer = theFormOn(model).answer();
    assertThat(answer).isNotNull();
    assertThat(answer.rows())
        .containsExactly(
            new ReadForm.PrintedRow(
                row.providerObjectId(),
                List.of(new ReadForm.PrintedFact("status", true, "paid", null, List.of()))));
  }

  @Test
  void a_question_asked_without_a_value_it_needs_is_refused_before_the_provider_is_reached() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();

    aPageOf(module)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1"),
            model);

    assertThat(module.theReadsItWasAskedFor()).isEmpty();
    ReadForm.Answer answer = theFormOn(model).answer();
    assertThat(answer).isNotNull();
    assertThat(answer.refusal()).contains("cannot be answered without your own identifier");
    assertThat(theFormOn(model).fields().get(0).value())
        .as("the values a refused question was filled in with are still in the boxes")
        .isEqualTo("tok_1");
  }

  @Test
  void a_question_asked_on_a_screen_naming_no_account_is_refused_and_asks_the_provider_nothing() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();

    aPageOf(module).answer(APROVIDER.value(), A_RUN, null, A_RETRACE, Map.of(), model);

    assertThat(module.theReadsItWasAskedFor()).isEmpty();
    ReadForm.Answer answer = theFormOn(model).answer();
    assertThat(answer).isNotNull();
    assertThat(answer.refusal()).isEqualTo(ReadControl.AN_ACCOUNT_IS_REQUIRED);
  }

  @Test
  void a_question_asked_with_no_question_named_is_refused_with_the_ones_this_provider_publishes() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();

    aPageOf(module).answer(APROVIDER.value(), A_RUN, ACCOUNT, null, Map.of(), model);

    assertThat(module.theReadsItWasAskedFor()).isEmpty();
    ReadForm.Answer answer = theFormOn(model).answer();
    assertThat(answer).isNotNull();
    assertThat(answer.refusal()).contains("answers one of these questions and no other");
  }

  @Test
  void a_provider_this_build_does_not_carry_is_refused_on_the_screen_itself() {
    Model model = new ConcurrentModel();

    String view =
        aPageOf(aModulePublishingTheRetrace())
            .render("nobody", A_RUN, ACCOUNT, A_RETRACE, Map.of(), model);

    assertThat(view).isEqualTo("reads");
    assertThat(model.getAttribute("refusal")).isNotNull();
    assertThat(model.getAttribute(ReadPage.THE_FORM)).isNull();
  }

  @Test
  void a_screen_opened_with_no_provider_named_offers_the_ones_this_build_carries() {
    Model model = new ConcurrentModel();

    aPageOf(aModulePublishingTheRetrace()).render(null, A_RUN, ACCOUNT, A_RETRACE, Map.of(), model);

    assertThat(model.getAttribute("choose")).isEqualTo(List.of(APROVIDER.value()));
    assertThat(model.getAttribute(ReadPage.THE_FORM)).isNull();
  }

  @Test
  void a_run_this_simulator_cannot_name_is_refused_on_the_screen_itself() {
    Model model = new ConcurrentModel();

    aPageOf(aModulePublishingTheRetrace())
        .render(APROVIDER.value(), "a run with spaces", ACCOUNT, A_RETRACE, Map.of(), model);

    assertThat(model.getAttribute("refusal")).isNotNull();
    assertThat(model.getAttribute(ReadPage.THE_FORM)).isNull();
  }

  @Test
  void a_provider_that_publishes_no_read_draws_what_it_has_instead_of_one() {
    Model model = new ConcurrentModel();

    aPageOf(new ProviderModuleDouble(APROVIDER.value()))
        .render(APROVIDER.value(), A_RUN, ACCOUNT, null, Map.of(), model);

    assertThat(theFormOn(model).panel()).isEqualTo(ReadForm.Panel.THIS_PROVIDER_PUBLISHES_NO_READ);
    assertThat(theFormOn(model).whatThisProviderPublishesInstead())
        .isEqualTo(SimulatorProvider.THIS_MODULE_PUBLISHES_NO_READ_A_TESTER_ASKS);
  }

  @Test
  void the_route_hands_the_whole_decision_to_the_page_and_decides_nothing_of_its_own() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    ReadController route = new ReadController(aPageOf(module));

    assertThat(
            route.reads(APROVIDER.value(), A_RUN, ACCOUNT, null, Map.of(), new ConcurrentModel()))
        .isEqualTo("reads");
    assertThat(
            route.theAnswerToIt(
                APROVIDER.value(),
                A_RUN,
                ACCOUNT,
                A_RETRACE,
                Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "1001"),
                new ConcurrentModel()))
        .isEqualTo("reads");
    assertThat(module.theReadsItWasAskedFor())
        .as("the second route is the one that asks the provider, and the first one never does")
        .hasSize(1);
  }

  @Test
  void the_screen_draws_the_answer_as_the_facts_the_provider_named_and_links_to_the_object() {
    AnsweredRow row =
        new AnsweredRow(
            "100003",
            List.of(
                new AnsweredRow.Fact("status", "paid"),
                new AnsweredRow.Fact("sum", "89.90"),
                new AnsweredRow.Fact(
                    "events",
                    new AnsweredRow.AnsweredValue.TheElementsUnderIt(
                        List.of(
                            new AnsweredRow.AnsweredValue.TheMembersUnderIt(
                                List.of(new AnsweredRow.Fact("type", "CHARGE"))))))));
    ProviderModuleDouble module =
        aModulePublishingTheRetrace().answeringARead(new ReadOutcome.Answered(List.of(row)));
    Model model = new ConcurrentModel();
    aPageOf(module)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "1001"),
            model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .contains("100003")
        .contains("status")
        .contains("89.90")
        .contains("providerObjectId=100003");
    assertThat(drawn)
        .as(
            "a member answered under a member is drawn under the name the provider answered it"
                + " under, with the facts of each of them beneath it")
        .contains("events")
        .contains(ReadForm.THE_PLACE_A_LIST_ANSWERED_IT_IN + 1)
        .contains("CHARGE")
        .doesNotContain("events.1.type");
  }

  @Test
  void the_screen_draws_what_a_provider_answered_about_the_query_beside_the_rows_it_answered() {
    ProviderModuleDouble module =
        aModulePublishingTheRetrace()
            .answeringARead(
                new ReadOutcome.Answered(
                    List.of(),
                    List.of(
                        new AnsweredRow.Fact("hasMore", "true"),
                        new AnsweredRow.Fact(
                            "maskedCard", new AnsweredRow.AnsweredValue.NoValueAtAll())),
                    null));
    Model model = new ConcurrentModel();
    aPageOf(module)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "9999"),
            model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .as(
            "what a provider answers about the question itself is no object of that provider, and"
                + " a screen reading it as one would leave the sentence that says what an empty"
                + " answer means unreachable")
        .contains("hasMore")
        .contains(NOTHING_FOUND)
        .contains(ReadForm.ANSWERED_THIS_WITH_NO_VALUE_AT_ALL)
        .contains(APROVIDER.value());
  }

  @Test
  void the_screen_draws_the_saved_cards_as_suggestions_on_a_box_a_tester_types_any_token_into() {
    ProviderModuleDouble module = aModulePublishingTheRetrace();
    Model model = new ConcurrentModel();
    aPageOf(module).render(APROVIDER.value(), A_RUN, ACCOUNT, A_RETRACE, Map.of(), model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .as(
            "a token is read by the provider, which refuses one it never issued, so this screen"
                + " suggests the cards this run saved and takes whatever a tester types instead")
        .contains("name=\"field.cardToken\"")
        .contains(
            "list=\"cardToken"
                + TheFieldsAFormDraws.THE_SUGGESTIONS_OF_WHAT_THIS_TEST_RUN_HAS_PRODUCED
                + "\"")
        .contains(THE_CARD_THIS_RUN_SAVED.label())
        .contains(
            asTheEngineWritesIt(
                TheFieldsAFormDraws
                    .THE_VALUES_THIS_RUN_PRODUCED_ARE_SUGGESTED_AND_THE_PROVIDER_JUDGES))
        .contains("name=\"field.myOwnIdentifier\"")
        .contains("/ui/reads/ask");
    assertThat(
            theSuggestionsOffered(
                    "cardToken",
                    TheFieldsAFormDraws.THE_SUGGESTIONS_OF_WHAT_THIS_TEST_RUN_HAS_PRODUCED)
                .matcher(drawn)
                .find())
        .as("the box takes its suggestions from a list naming what this run produced")
        .isTrue();
    assertThat(A_LIST_FOR_A_FIELD.matcher(drawn).find())
        .as(
            "no value this read sends is read by this simulator, so no field of this screen is a"
                + " list a tester can only pick a value from")
        .isFalse();
    assertThat(drawn.split(THE_PAIRS_A_SCREEN_DRAWS_A_NAME_AND_A_VALUE_IN, -1).length - 1)
        .as(
            "the pairs on a form nobody has asked yet are the values suggested on one box and the"
                + " names and keys this question sends, because the walk this screen draws an"
                + " answer's facts with is drawn under an answer and never where it is defined")
        .isEqualTo(2);
  }

  @Test
  void the_screen_draws_a_vocabulary_this_provider_publishes_as_suggestions_on_a_box_as_well() {
    ProviderModuleDouble module =
        new ProviderModuleDouble(APROVIDER.value())
            .publishing(A_READ_WHOSE_VALUES_ITS_PROVIDER_PUBLISHES);
    Model model = new ConcurrentModel();
    aPageOf(module).render(APROVIDER.value(), A_RUN, ACCOUNT, A_LISTING, Map.of(), model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .as(
            "a read declaring the values its provider publishes is drawn the way the wizard draws"
                + " one, so the refusal that provider publishes for a value it does not name is"
                + " reachable by typing it here")
        .contains("name=\"field." + STATUS + "\"")
        .contains(
            "list=\""
                + STATUS
                + TheFieldsAFormDraws.THE_SUGGESTIONS_OF_A_VOCABULARY_A_PROVIDER_PUBLISHES
                + "\"")
        .contains("taken and not yet settled")
        .contains(
            asTheEngineWritesIt(
                TheFieldsAFormDraws
                    .THE_VALUES_THIS_PROVIDER_PUBLISHES_ARE_OFFERED_AND_NONE_IS_IMPOSED));
    assertThat(
            theSuggestionsOffered(
                    STATUS,
                    TheFieldsAFormDraws.THE_SUGGESTIONS_OF_A_VOCABULARY_A_PROVIDER_PUBLISHES)
                .matcher(drawn)
                .find())
        .as("the values this provider publishes are the suggestions on the box, not its gate")
        .isTrue();
    assertThat(A_LIST_FOR_A_FIELD.matcher(drawn).find()).isFalse();
  }

  @Test
  void the_screen_says_that_nothing_found_is_this_providers_answer_and_not_a_failed_read() {
    ProviderModuleDouble module =
        aModulePublishingTheRetrace().answeringARead(new ReadOutcome.Answered(List.of()));
    Model model = new ConcurrentModel();
    aPageOf(module)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "9999"),
            model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn).contains(NOTHING_FOUND);
  }

  @Test
  void the_screen_draws_the_way_to_configure_an_account_when_this_run_configures_none() {
    Model model = new ConcurrentModel();
    new ReadPage(
            new ProviderRegistry(List.of(aModulePublishingTheRetrace())),
            new NoConfiguredAccount(),
            new RecordedActions().asTrail(),
            "default")
        .render(APROVIDER.value(), A_RUN, null, null, Map.of(), model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .contains(ReadForm.THE_RUN_CONFIGURES_NO_ACCOUNT)
        .contains("/ui/configuration/account");
  }

  @Test
  void the_screen_draws_what_a_provider_publishing_no_read_has_instead_and_where_to_read_it() {
    Model model = new ConcurrentModel();
    aPageOf(new ProviderModuleDouble(APROVIDER.value()))
        .render(APROVIDER.value(), A_RUN, ACCOUNT, null, Map.of(), model);

    String drawn =
        ScreensThisTestDraws.drawn(
            ReadPage.VIEW, model, new ContributedScreens(List.of()), APROVIDER.value());

    assertThat(drawn)
        .as(
            "a tester reads what this provider has in place of a read and where what this run"
                + " holds is read instead")
        .contains("publishes no read a tester asks with identifiers of their own")
        .contains("/ui/objects");
  }

  @Test
  void asking_the_question_is_written_to_the_trail_as_an_action_of_the_screen() {
    AnsweredRow row = new AnsweredRow("100003", List.of(new AnsweredRow.Fact(STATUS, "paid")));
    ProviderModuleDouble module =
        aModulePublishingTheRetrace()
            .answeringARead(new ReadOutcome.Answered(List.of(row), "scn_7"));
    RecordedActions trail = new RecordedActions();

    aPageOf(module, trail)
        .answer(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1", "field.myOwnIdentifier", "1001"),
            new ConcurrentModel());

    assertThat(trail.written())
        .as(
            "a rule of this test run can answer a read and move the story it stands on, so a"
                + " tester who closed the screen still finds on the trail that they asked and"
                + " which rule answered")
        .hasSize(1);
    AuditRecord written = trail.written().getFirst();
    assertThat(written.provider()).isEqualTo(APROVIDER);
    assertThat(written.testRunId()).isEqualTo(TestRunId.of(A_RUN));
    assertThat(written.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(written.method()).isEqualTo("GET");
    assertThat(written.path()).isEqualTo("/ui/reads/ask");
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.scenarioId()).isEqualTo("scn_7");
    assertThat(written.providerObjectId()).isEqualTo("100003");
    assertThat(written.sanitizedBody())
        .containsEntry(TheTrailRowAReadLeaves.READ_NAMED, List.of(A_RETRACE))
        .containsEntry("cardToken", List.of("tok_1"))
        .containsEntry(TheTrailRowAReadLeaves.ROWS_ANSWERED, List.of("1"));
  }

  @Test
  void opening_the_screen_without_asking_writes_nothing_to_the_trail() {
    RecordedActions trail = new RecordedActions();

    aPageOf(aModulePublishingTheRetrace(), trail)
        .render(
            APROVIDER.value(),
            A_RUN,
            ACCOUNT,
            A_RETRACE,
            Map.of("field.cardToken", "tok_1"),
            new ConcurrentModel());

    assertThat(trail.written())
        .as("a tester who only drew the form asked the provider nothing, so nothing happened")
        .isEmpty();
  }

  @Test
  void a_question_the_screen_refused_before_the_provider_was_reached_is_written_all_the_same() {
    RecordedActions trail = new RecordedActions();

    aPageOf(aModulePublishingTheRetrace(), trail)
        .answer(APROVIDER.value(), A_RUN, ACCOUNT, A_RETRACE, Map.of(), new ConcurrentModel());

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(400);
    assertThat(written.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.sanitizedBody()).containsKey(TheTrailRowAReadLeaves.REFUSED_BECAUSE);
  }

  private static ReadForm.View theFormOn(Model model) {
    return (ReadForm.View) model.getAttribute(ReadPage.THE_FORM);
  }

  private static ProviderModuleDouble aModulePublishingTheRetrace() {
    return new ProviderModuleDouble(APROVIDER.value())
        .publishing(DECLARED)
        .havingProduced("cardToken", THE_CARD_THIS_RUN_SAVED);
  }

  private static ReadPage aPageOf(ProviderModuleDouble module) {
    return aPageOf(module, new RecordedActions());
  }

  private static ReadPage aPageOf(ProviderModuleDouble module, RecordedActions trail) {
    return new ReadPage(
        new ProviderRegistry(List.of(module)),
        new OneConfiguredAccount(),
        trail.asTrail(),
        "default");
  }

  private static final class NoConfiguredAccount implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }
  }

  private static final class OneConfiguredAccount implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.of(ACCOUNT);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }
  }
}
