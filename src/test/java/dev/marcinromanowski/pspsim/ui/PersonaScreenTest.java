package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.SeededPersona;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonaScreenTest {

  private static final ProviderId PROVIDER = ProviderId.of("screened");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final String THE_CARD_IS_THE_PUBLISHED_ONE = "The card is the published sample.";

  private static final List<String> ALL_THREE_OF_THEM =
      List.of("persona-1", "persona-2", "persona-3");

  private static final List<String> THE_FIRST_TWO_OF_THEM = List.of("persona-1", "persona-2");

  private static final String AN_ACCOUNT_IS_CONFIGURED_FOR_IT =
      "Seed it into an account this run configures.";

  private static final String THE_TWO_SENTENCES_A_PROVIDER_ENDS_EVERY_PERSONA_ON =
      THE_CARD_IS_THE_PUBLISHED_ONE + " " + AN_ACCOUNT_IS_CONFIGURED_FOR_IT;

  private static final String A_PROVIDER_ANSWERS_IN_TWO_SENTENCES =
      "The provider answers \"Do not honour. Contact the issuer.\" and stops there.";

  @Test
  void the_screen_shows_each_persona_with_the_presets_it_writes_and_the_ids_it_will_mint() {
    PersonaScreen.View view = PersonaScreen.of("screened", "run-a", aCatalogue(), List.of());

    assertThat(view.personas())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.name()).isEqualTo("a-card-that-works");
              assertThat(row.intent()).isEqualTo("a-saved-card-that-succeeds");
              assertThat(row.presets()).containsExactly("preauth-success-capture-success");
              assertThat(row.theRunAlreadyHoldsIt()).isFalse();
              assertThat(row.instruments())
                  .singleElement()
                  .satisfies(
                      instrument -> {
                        assertThat(instrument.alias()).isEqualTo("a-card-that-works");
                        assertThat(instrument.providerNativeIds()).containsExactly("token");
                        assertThat(instrument.maskedCard()).isEqualTo("**** 1111");
                        assertThat(instrument.expiry()).isEqualTo("12/2030");
                        assertThat(instrument.howThoseAreMinted())
                            .isEqualTo("a minting of its own");
                        assertThat(instrument.triggersTheRuleAt()).isEqualTo(0);
                      });
            });
  }

  @Test
  void the_screen_says_which_personas_this_run_already_holds_and_which_it_does_not() {
    PersonaScreen.View held =
        PersonaScreen.of(
            "screened", "run-a", aCatalogue(), List.of(aFixtureAliased("a-card-that-works")));
    PersonaScreen.View notHeld =
        PersonaScreen.of(
            "screened", "run-a", aCatalogue(), List.of(aFixtureAliased("something-else")));

    assertThat(held.personas().get(0).theRunAlreadyHoldsIt()).isTrue();
    assertThat(notHeld.personas().get(0).theRunAlreadyHoldsIt()).isFalse();
  }

  @Test
  void
      the_screen_names_every_intent_this_provider_publishes_no_persona_for_and_what_it_has_instead() {
    PersonaScreen.View view = PersonaScreen.of("screened", "run-a", aCatalogue(), List.of());

    assertThat(view.uncovered())
        .hasSize(PersonaIntent.values().length - 1)
        .anySatisfy(
            gap -> {
              assertThat(gap.intent()).isEqualTo("a-saved-card-that-is-declined");
              assertThat(gap.instead()).isEqualTo("this provider declines nothing");
              assertThat(gap.whatATesterGets())
                  .isEqualTo(PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED.whatATesterGets());
            });
  }

  @Test
  void the_sentences_more_than_one_persona_ends_on_are_named_once_and_left_out_of_every_row() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A stored card this run will capture. "
                    + THE_TWO_SENTENCES_A_PROVIDER_ENDS_EVERY_PERSONA_ON,
                "A stored card this run will decline. "
                    + THE_TWO_SENTENCES_A_PROVIDER_ENDS_EVERY_PERSONA_ON,
                "A stored card whose answer is lost. "
                    + THE_TWO_SENTENCES_A_PROVIDER_ENDS_EVERY_PERSONA_ON),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A stored card this run will capture.",
            "A stored card this run will decline.",
            "A stored card whose answer is lost.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats())
        .singleElement()
        .satisfies(
            shared -> {
              assertThat(shared.fact())
                  .isEqualTo(THE_TWO_SENTENCES_A_PROVIDER_ENDS_EVERY_PERSONA_ON);
              assertThat(shared.personas()).isEqualTo(ALL_THREE_OF_THEM);
            });
  }

  @Test
  void a_persona_that_says_only_what_another_ends_on_names_both_and_shows_nothing_of_its_own() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A stored card this run will decline. " + THE_CARD_IS_THE_PUBLISHED_ONE,
                THE_CARD_IS_THE_PUBLISHED_ONE),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A stored card this run will decline.", PersonaScreen.IT_GIVES_NOTHING_OF_ITS_OWN);
    assertThat(view.theFactsMoreThanOnePersonaRepeats())
        .singleElement()
        .satisfies(
            shared -> {
              assertThat(shared.fact()).isEqualTo(THE_CARD_IS_THE_PUBLISHED_ONE);
              assertThat(shared.personas()).isEqualTo(THE_FIRST_TWO_OF_THEM);
            });
  }

  @Test
  void a_provider_whose_personas_end_on_no_shared_sentence_keeps_every_row_whole() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card that works. It is billed in euros.",
                "A wallet that declines. It is billed in forints.",
                "A transfer that is lost. It is billed in dollars."),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A card that works. It is billed in euros.",
            "A wallet that declines. It is billed in forints.",
            "A transfer that is lost. It is billed in dollars.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats()).isEmpty();
  }

  @Test
  void the_only_persona_a_provider_publishes_repeats_nothing_and_keeps_all_of_its_words() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A stored card this run will capture. " + THE_CARD_IS_THE_PUBLISHED_ONE),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly("A stored card this run will capture. " + THE_CARD_IS_THE_PUBLISHED_ONE);
    assertThat(view.theFactsMoreThanOnePersonaRepeats()).isEmpty();
  }

  @Test
  void a_sentence_two_personas_share_in_the_middle_is_not_a_sentence_they_end_on() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card that works. " + THE_CARD_IS_THE_PUBLISHED_ONE + " It ends here.",
                "A card that declines. " + THE_CARD_IS_THE_PUBLISHED_ONE + " It ends elsewhere."),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A card that works. " + THE_CARD_IS_THE_PUBLISHED_ONE + " It ends here.",
            "A card that declines. " + THE_CARD_IS_THE_PUBLISHED_ONE + " It ends elsewhere.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats()).isEmpty();
  }

  @Test
  void a_fact_two_of_them_repeat_and_a_fact_all_of_them_repeat_each_name_everyone_who_gives_it() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card that works. "
                    + THE_CARD_IS_THE_PUBLISHED_ONE
                    + " "
                    + AN_ACCOUNT_IS_CONFIGURED_FOR_IT,
                "A card that declines. "
                    + THE_CARD_IS_THE_PUBLISHED_ONE
                    + " "
                    + AN_ACCOUNT_IS_CONFIGURED_FOR_IT,
                "A card whose answer is lost. " + AN_ACCOUNT_IS_CONFIGURED_FOR_IT),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A card that works.", "A card that declines.", "A card whose answer is lost.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats())
        .satisfiesExactly(
            shared -> {
              assertThat(shared.fact()).isEqualTo(THE_CARD_IS_THE_PUBLISHED_ONE);
              assertThat(shared.personas()).isEqualTo(THE_FIRST_TWO_OF_THEM);
            },
            shared -> {
              assertThat(shared.fact()).isEqualTo(AN_ACCOUNT_IS_CONFIGURED_FOR_IT);
              assertThat(shared.personas()).isEqualTo(ALL_THREE_OF_THEM);
            });
  }

  @Test
  void a_sentence_all_of_them_repeat_is_printed_once_and_not_again_inside_a_longer_repetition() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card that works. "
                    + THE_CARD_IS_THE_PUBLISHED_ONE
                    + " "
                    + AN_ACCOUNT_IS_CONFIGURED_FOR_IT,
                "A card that declines. "
                    + THE_CARD_IS_THE_PUBLISHED_ONE
                    + " "
                    + AN_ACCOUNT_IS_CONFIGURED_FOR_IT,
                "A card whose answer is lost. " + AN_ACCOUNT_IS_CONFIGURED_FOR_IT),
            List.of());

    assertThat(howOftenTheScreenSays(view, AN_ACCOUNT_IS_CONFIGURED_FOR_IT)).isEqualTo(1);
    assertThat(howOftenTheScreenSays(view, THE_CARD_IS_THE_PUBLISHED_ONE)).isEqualTo(1);
  }

  @Test
  void a_period_inside_a_quotation_is_not_the_end_of_a_sentence_and_no_row_is_cut_inside_one() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card this run captures, and " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES,
                "A card this run declines, and " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly(
            "A card this run captures, and " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES,
            "A card this run declines, and " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES);
    assertThat(view.theFactsMoreThanOnePersonaRepeats()).isEmpty();
  }

  @Test
  void a_quotation_of_two_sentences_travels_whole_into_the_fact_more_than_one_of_them_repeats() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(
                "A card that works. " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES,
                "A card that declines. " + A_PROVIDER_ANSWERS_IN_TWO_SENTENCES),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly("A card that works.", "A card that declines.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats())
        .singleElement()
        .satisfies(
            shared -> {
              assertThat(shared.fact()).isEqualTo(A_PROVIDER_ANSWERS_IN_TWO_SENTENCES);
              assertThat(shared.personas()).isEqualTo(THE_FIRST_TWO_OF_THEM);
            });
  }

  @Test
  void two_personas_that_give_a_tester_the_same_words_say_so_rather_than_showing_an_empty_cell() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives(THE_CARD_IS_THE_PUBLISHED_ONE, THE_CARD_IS_THE_PUBLISHED_ONE),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsOnly(PersonaScreen.IT_GIVES_NOTHING_OF_ITS_OWN);
    assertThat(view.theFactsMoreThanOnePersonaRepeats())
        .singleElement()
        .satisfies(
            shared -> {
              assertThat(shared.fact()).isEqualTo(THE_CARD_IS_THE_PUBLISHED_ONE);
              assertThat(shared.personas()).isEqualTo(THE_FIRST_TWO_OF_THEM);
            });
  }

  @Test
  void words_that_end_in_a_space_end_at_their_last_word_and_repeat_nothing() {
    PersonaScreen.View view =
        PersonaScreen.of(
            "screened",
            "run-a",
            whatEachOfThemGives("A card that works. ", "A card that declines. "),
            List.of());

    assertThat(view.personas())
        .extracting(PersonaScreen.Row::whatItGives)
        .containsExactly("A card that works.", "A card that declines.");
    assertThat(view.theFactsMoreThanOnePersonaRepeats()).isEmpty();
  }

  @Test
  void an_instrument_that_describes_no_card_shows_no_brand_no_mask_no_expiry_and_no_funding() {
    DeclaredPersona wallet =
        new DeclaredPersona(
            PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
            "a-wallet-that-works",
            "a wallet this provider will charge",
            List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
            List.of(
                new PersonaFixture(
                    "a-wallet-that-works",
                    "an-owner",
                    PaymentMethodKind.WALLET,
                    null,
                    List.of(),
                    "",
                    Map::of,
                    null)));

    PersonaScreen.Proposal proposal =
        PersonaScreen.proposalOf(wallet, List.of(aProposedRule()), null);

    assertThat(proposal.instruments())
        .singleElement()
        .satisfies(
            instrument -> {
              assertThat(instrument.type()).isEqualTo("wallet");
              assertThat(instrument.brand()).isNull();
              assertThat(instrument.maskedCard()).isNull();
              assertThat(instrument.expiry()).isNull();
              assertThat(instrument.funding()).isNull();
            });
  }

  @Test
  void a_proposal_shows_the_rules_a_persona_would_write_and_the_instruments_beside_them() {
    PersonaScreen.Proposal proposal =
        PersonaScreen.proposalOf(aPersona(), List.of(aProposedRule()), null);

    assertThat(proposal.persona()).isEqualTo("a-card-that-works");
    assertThat(proposal.presets()).isEqualTo("preauth-success-capture-success");
    assertThat(proposal.rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.operation()).isEqualTo("capture");
              assertThat(rule.action()).isEqualTo("FIXED_SUCCESS");
            });
    assertThat(proposal.instruments())
        .singleElement()
        .satisfies(instrument -> assertThat(instrument.alias()).isEqualTo("a-card-that-works"));
  }

  @Test
  void what_was_created_names_every_object_by_the_id_its_own_screen_files_it_under() {
    PersonaScreen.Created created =
        PersonaScreen.createdOf(
            new SeededPersona(
                "a-card-that-works",
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                RUN,
                List.of(aWrittenRule("sc_1")),
                List.of(aFixtureAliased("a-card-that-works"))));

    assertThat(created.testRunId()).isEqualTo("run-a");
    assertThat(created.rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.scenarioId()).isEqualTo("sc_1");
              assertThat(rule.operation()).isEqualTo("capture");
              assertThat(rule.action()).isEqualTo("FIXED_SUCCESS");
            });
    assertThat(created.instruments())
        .singleElement()
        .satisfies(
            instrument -> {
              assertThat(instrument.fixtureId()).isEqualTo("fx_1");
              assertThat(instrument.alias()).isEqualTo("a-card-that-works");
              assertThat(instrument.providerNativeIds()).containsExactly("token tok_1");
              assertThat(instrument.defaultScenario()).isEqualTo("sc_1");
            });
  }

  @Test
  void what_a_persona_created_is_addressable_by_the_ids_a_harness_reads_back() {
    SeededPersona created =
        new SeededPersona(
            "a-card-that-works",
            PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
            RUN,
            List.of(aWrittenRule("sc_1"), aWrittenRule("sc_2")),
            List.of(aFixtureAliased("a-card-that-works")));

    assertThat(created.theScenarioIdsItWrote()).containsExactly("sc_1", "sc_2");
    assertThat(created.theFixtureIdsItSeeded()).containsExactly("fx_1");
  }

  @Test
  void a_module_that_overrides_nothing_declines_every_intent_rather_than_publishing_a_persona() {
    ProviderPersonas published = new ProviderModuleDouble("unstated").personas(new CountingIds());

    assertThat(published.personas()).isEmpty();
    for (PersonaIntent intent : PersonaIntent.values()) {
      assertThat(published.intentsItPublishesNoneFor().get(intent))
          .isEqualTo(SimulatorProvider.THIS_MODULE_PUBLISHES_NO_PERSONA);
    }
  }

  @Test
  void the_refusal_of_objects_it_did_not_create_names_the_persona_it_only_showed() {
    assertThat(PersonaScreen.theRefusalOfObjectsItDidNotCreate().httpStatus()).isEqualTo(400);
    assertThat(PersonaScreen.theRefusalOfObjectsItDidNotCreate().subject().theObjectItNames())
        .isEmpty();
  }

  private static int howOftenTheScreenSays(PersonaScreen.View view, String sentence) {
    int said = 0;
    for (PersonaScreen.Row row : view.personas()) {
      said += row.whatItGives().contains(sentence) ? 1 : 0;
    }
    for (PersonaScreen.SharedFact shared : view.theFactsMoreThanOnePersonaRepeats()) {
      said += shared.fact().contains(sentence) ? 1 : 0;
    }
    return said;
  }

  private static ProviderPersonas whatEachOfThemGives(String... sayings) {
    List<DeclaredPersona> published = new ArrayList<>();
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    PersonaIntent[] intents = PersonaIntent.values();
    for (int at = 0; at < intents.length; at++) {
      if (at < sayings.length) {
        published.add(aPersonaNamed("persona-" + (at + 1), intents[at], sayings[at]));
      } else {
        declined.put(intents[at], "this provider publishes nothing of the kind");
      }
    }
    return new ProviderPersonas(PROVIDER, published, declined);
  }

  private static DeclaredPersona aPersonaNamed(
      String name, PersonaIntent intent, String whatItGives) {
    return new DeclaredPersona(
        intent,
        name,
        whatItGives,
        List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
        List.of(
            new PersonaFixture(
                name,
                "an-owner",
                PaymentMethodKind.CARD,
                new FixtureCard("VISA", "1111", 12, 2030, "credit"),
                List.of(ProviderPaymentMethodFields.TOKEN),
                "a minting of its own",
                () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
                0)));
  }

  private static ProviderPersonas aCatalogue() {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    declined.put(PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED, "this provider declines nothing");
    declined.put(
        PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
        "this provider drops no connection");
    return new ProviderPersonas(PROVIDER, List.of(aPersona()), declined);
  }

  private static DeclaredPersona aPersona() {
    return new DeclaredPersona(
        PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
        "a-card-that-works",
        "a stored card this provider will capture",
        List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
        List.of(
            new PersonaFixture(
                "a-card-that-works",
                "an-owner",
                PaymentMethodKind.CARD,
                new FixtureCard("VISA", "1111", 12, 2030, "credit"),
                List.of(ProviderPaymentMethodFields.TOKEN),
                "a minting of its own",
                () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
                0)));
  }

  private static NewScenarioRule aProposedRule() {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming("capture"),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static ScenarioRule aWrittenRule(String scenarioId) {
    return new ScenarioRule(
        scenarioId,
        PROVIDER,
        RUN,
        0,
        1,
        ScenarioMatch.anyRequest().naming("capture"),
        new ScenarioAction.FixedSuccess(),
        null,
        Instant.EPOCH);
  }

  private static PaymentMethodFixture aFixtureAliased(String alias) {
    return new PaymentMethodFixture(
        "fx_1",
        PROVIDER,
        RUN,
        "acct-1",
        "an-owner",
        PaymentMethodKind.CARD,
        alias,
        new FixtureCard("VISA", "1111", 12, 2030, "credit"),
        Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
        "sc_1",
        Instant.EPOCH);
  }
}
