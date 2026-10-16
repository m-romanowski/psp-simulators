package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountFormTest {

  private static final String RUN = "account-form-test";

  private static final String SECRET_TYPED = "sk-live-must-never-come-back";

  private static final ProviderAccountFields FIELDS =
      new ProviderAccountFields(
          ProviderId.of("aprovider"),
          List.of(
              new ConfigurableField(
                  "userId",
                  "The user id",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "terminalId",
                  "Terminal id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "captureWindow",
                  "How long a hold lasts",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read")));

  private static final ConfigurableField A_CLOSED_SET =
      new ConfigurableField(
          "wireForm",
          "which wire form answers carry",
          "a sentence a test does not read",
          false,
          false,
          List.of(
              new AcceptedValue("LIVE_WIRE", "answer text/html, as the live wire does"),
              new AcceptedValue(
                  "PUBLISHED_EXAMPLE", "answer application/json, as the published example does")));

  private static final ProviderAccountFields SPANS =
      new ProviderAccountFields(
          ProviderId.of("aprovider"),
          List.of(
              new ConfigurableField(
                  "terminalId",
                  "Terminal id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              ConfigurableField.aDuration(
                  "captureWindow",
                  "how long a hold is capturable",
                  "a sentence a test does not read",
                  false,
                  "P7D"),
              ConfigurableField.aDuration(
                  "webhookTimestampAge",
                  "how old the signed timestamp is",
                  "a sentence a test does not read",
                  false,
                  "PT6M"),
              ConfigurableField.aDuration(
                  "settlementDelay",
                  "how late a settlement lands",
                  "a sentence a test does not read",
                  false,
                  "a fortnight")));

  private static final ConfigurableField A_SHAPE =
      new ConfigurableField(
          "captureWindow",
          "how long a hold lasts",
          "a sentence a test does not read",
          false,
          false,
          List.of(),
          "P7D");

  private static final ProviderAccountFields FIELDS_WITH_A_CLOSED_SET =
      new ProviderAccountFields(
          ProviderId.of("aprovider"),
          List.of(
              new ConfigurableField(
                  "terminalId",
                  "Terminal id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              A_SHAPE,
              A_CLOSED_SET));

  @Test
  void a_field_whose_values_are_a_closed_set_is_offered_as_the_sentences_the_provider_wrote() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    AccountForm.Field offered = theFieldNamed(asked, "wireForm");
    assertThat(offered.isTypedIn())
        .as("a form that drew a text box here would ask a tester to guess the two wire values")
        .isFalse();
    assertThat(offered.choices())
        .extracting(AccountForm.Choice::whatItMeans)
        .containsExactly(
            "answer text/html, as the live wire does",
            "answer application/json, as the published example does");
    assertThat(offered.choices())
        .extracting(AccountForm.Choice::value)
        .containsExactly("LIVE_WIRE", "PUBLISHED_EXAMPLE");
  }

  @Test
  void a_field_the_provider_declares_no_values_for_is_still_a_box_a_tester_types_into() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    AccountForm.Field typedIn = theFieldNamed(asked, "terminalId");
    assertThat(typedIn.isTypedIn()).isTrue();
    assertThat(typedIn.choices()).isEmpty();
  }

  @Test
  void a_box_that_takes_a_shape_hints_that_shape_rather_than_the_wire_name_of_the_field() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(theFieldNamed(asked, "captureWindow").theHintTheBoxShows())
        .as("a tester reading captureWindow in a grey box learns nothing about what it takes")
        .isEqualTo("P7D");
  }

  @Test
  void every_box_hints_a_worked_example_and_never_the_name_the_provider_reads_it_under() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(theFieldNamed(asked, "terminalId").theHintTheBoxShows())
        .as(
            "a box hints the example its provider declared; terminalId is the name that provider"
                + " reads the value under, which is a developer's word for the field and nothing"
                + " a tester could type into it")
        .isEqualTo("a value a test does not read")
        .isNotEqualTo("terminalId");
    assertThat(asked.accountIdentity().theHintTheBoxShows())
        .as(
            "and the two boxes the screen owns rather than the provider hint an example of their"
                + " own, so no box on this form falls back on a key")
        .isEqualTo(AccountForm.AN_IDENTITY_A_TESTER_WOULD_NAME);
    assertThat(asked.callbackTarget().theHintTheBoxShows())
        .isEqualTo(AccountForm.A_CALLBACK_TARGET_A_TESTER_WOULD_NAME);
  }

  @Test
  void a_refused_form_comes_back_with_the_value_a_tester_picked_still_picked() {
    AccountForm.View refused =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS_WITH_A_CLOSED_SET,
            Map.of("wireForm", "PUBLISHED_EXAMPLE"),
            Map.of(),
            List.of(),
            null,
            List.of(),
            ConfigurationRefusal.of("accounts[0].configuration.terminalId"));

    assertThat(theFieldNamed(refused, "wireForm").choices())
        .filteredOn(AccountForm.Choice::chosen)
        .extracting(AccountForm.Choice::value)
        .containsExactly("PUBLISHED_EXAMPLE");
  }

  @Test
  void a_form_nobody_has_picked_a_value_on_yet_comes_back_with_none_of_them_picked() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(theFieldNamed(asked, "wireForm").choices())
        .as("the empty entry the form draws above them is the one a browser then selects")
        .noneMatch(AccountForm.Choice::chosen);
  }

  @Test
  void a_value_the_api_took_that_this_provider_no_longer_offers_leaves_nothing_picked() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS_WITH_A_CLOSED_SET,
            Map.of("wireForm", "RETIRED_FORM"),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(theFieldNamed(asked, "wireForm").choices())
        .as(
            "the control API accepts anything and is refused by the provider, so a value no"
                + " longer offered must not come back marked as the one picked")
        .noneMatch(AccountForm.Choice::chosen);
  }

  @Test
  void the_form_asks_for_a_providers_own_fields_by_the_label_a_tester_reads() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider", RUN, null, FIELDS, Map.of(), Map.of(), List.of(), null, List.of(), null);

    assertThat(asked.fields())
        .extracting(AccountForm.Field::label)
        .containsExactly("The user id", "Terminal id", "How long a hold lasts");
    assertThat(asked.fields())
        .extracting(AccountForm.Field::required)
        .containsExactly(true, true, false);
  }

  @Test
  void every_field_carries_the_sentence_the_provider_wrote_about_it() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider", RUN, null, FIELDS, Map.of(), Map.of(), List.of(), null, List.of(), null);

    assertThat(asked.fields())
        .extracting(AccountForm.Field::whatItIsFor)
        .containsExactly(
            "a sentence a test does not read",
            "a sentence a test does not read",
            "a sentence a test does not read");
  }

  @Test
  void the_identity_the_configuration_call_is_refused_without_is_a_required_field_of_the_form() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    AccountForm.Field identity = asked.accountIdentity();
    assertThat(identity.required())
        .as(
            "the grammar refuses accounts[0].accountId when it is blank, so the form that sends it"
                + " must read as required and not as a field with no state at all")
        .isTrue();
    assertThat(identity.label()).isEqualTo("account identity");
    assertThat(identity.value()).isEqualTo("merchant-1");
    assertThat(identity.whatItIsFor()).isEqualTo(AccountForm.THE_IDENTITY_NAMES_THE_ACCOUNT);
    assertThat(identity.secret()).isFalse();
  }

  @Test
  void the_callback_target_a_run_may_configure_none_of_is_not_a_required_field_of_the_form() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            Map.of(),
            Map.of(),
            List.of(),
            "https://merchant.example/callbacks",
            List.of(),
            null);

    AccountForm.Field target = asked.callbackTarget();
    assertThat(target.required()).isFalse();
    assertThat(target.label()).isEqualTo("callback target for this account");
    assertThat(target.value()).isEqualTo("https://merchant.example/callbacks");
    assertThat(target.whatItIsFor()).isEqualTo(AccountForm.WHERE_THIS_ACCOUNT_S_CALLBACKS_GO);
  }

  @Test
  void the_form_counts_the_identity_and_the_callback_target_among_the_fields_it_asks_for() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider", RUN, null, FIELDS, Map.of(), Map.of(), List.of(), null, List.of(), null);

    assertThat(asked.fieldsThisFormHas())
        .as("the three the provider declares, plus the identity and the callback target")
        .isEqualTo(5);
    assertThat(asked.fieldsThisFormRequires())
        .as("userId and terminalId are required, and so is the identity; captureWindow is not")
        .isEqualTo(3);
    assertThat(asked.everyFieldThisFormAsksFor())
        .extracting(AccountForm.Field::key)
        .containsExactly("accountId", "userId", "terminalId", "captureWindow", "callbackUrl");
  }

  @Test
  void a_form_that_asks_for_a_secret_says_once_what_being_a_secret_costs_a_field() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider", RUN, null, FIELDS, Map.of(), Map.of(), List.of(), null, List.of(), null);

    assertThat(asked.asksForASecret())
        .as("userId is this provider's secret, so the form has a badge to explain")
        .isTrue();
  }

  @Test
  void a_form_that_asks_for_no_secret_explains_no_badge_it_never_draws() {
    ProviderAccountFields nothingSecret =
        new ProviderAccountFields(
            ProviderId.of("aprovider"),
            List.of(
                new ConfigurableField(
                    "terminalId",
                    "Terminal id",
                    "a sentence",
                    true,
                    false,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "captureWindow",
                    "How long a hold lasts",
                    "another sentence",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read")));

    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            nothingSecret,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.asksForASecret())
        .as(
            "neither the identity, nor the callback target, nor a field of this provider is a"
                + " secret, so a legend about the secret badge would name a badge nothing wears."
                + " Every provider this build serves declares at least one"
                + " secret, so nothing in it reaches this answer and no test of any kind had ever"
                + " asked for it: it is the floor a provider with no secret lands on")
        .isFalse();
  }

  @Test
  void a_form_whose_provider_requires_nothing_still_requires_the_identity_and_nothing_more() {
    ProviderAccountFields nothingRequired =
        new ProviderAccountFields(
            ProviderId.of("aprovider"),
            List.of(
                new ConfigurableField(
                    "captureWindow",
                    "How long a hold lasts",
                    "a sentence",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read")));

    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            nothingRequired,
            Map.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.fieldsThisFormRequires()).isEqualTo(1);
    assertThat(asked.fieldsThisFormHas()).isEqualTo(3);
  }

  @Test
  void a_refused_form_keeps_what_was_typed_into_every_field_that_is_not_a_secret() {
    AccountForm.View refused = refusedWith("terminalId is already configured");

    assertThat(refused.fields())
        .filteredOn(field -> !field.secret())
        .extracting(AccountForm.Field::value)
        .containsExactly("7777", "P3D");
  }

  @Test
  void a_refused_form_hands_a_secret_back_empty_and_says_on_screen_that_it_must_be_retyped() {
    AccountForm.View refused = refusedWith("terminalId is already configured");

    assertThat(refused.fields())
        .filteredOn(AccountForm.Field::secret)
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.value()).isEmpty();
              assertThat(field.mustBeRetyped()).isTrue();
            });
    assertThat(refused.secretsMustBeRetyped()).isTrue();
  }

  @Test
  void nothing_anywhere_in_a_refused_form_carries_the_secret_that_was_typed_into_it() {
    AccountForm.View refused = refusedWith("terminalId is already configured");

    assertThat(refused.toString()).doesNotContain(SECRET_TYPED);
  }

  @Test
  void a_form_that_was_not_refused_asks_no_one_to_retype_anything() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.fields()).noneMatch(AccountForm.Field::mustBeRetyped);
    assertThat(asked.secretsMustBeRetyped()).isFalse();
  }

  @Test
  void a_secret_is_left_off_the_form_even_before_anything_was_refused() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.fields())
        .filteredOn(AccountForm.Field::secret)
        .singleElement()
        .satisfies(field -> assertThat(field.value()).isEmpty());
  }

  @Test
  void the_field_a_provider_refused_is_the_one_the_form_marks_and_no_other_is() {
    AccountForm.View refused = refusedWith("terminalId is already configured");

    assertThat(refused.fields())
        .filteredOn(AccountForm.Field::refused)
        .extracting(AccountForm.Field::key)
        .containsExactly("terminalId");
  }

  @Test
  void a_field_that_holds_a_block_of_many_lines_is_drawn_as_a_block_and_not_as_a_one_line_box() {
    ProviderAccountFields withABlock =
        new ProviderAccountFields(
            ProviderId.of("aprovider"),
            List.of(
                new ConfigurableField(
                    "terminalId",
                    "Terminal id",
                    "a sentence a test does not read",
                    true,
                    false,
                    List.of(),
                    "7777"),
                ConfigurableField.aBlockOfManyLines(
                    "apiKey",
                    "merchant public key (PEM)",
                    "a sentence a test does not read",
                    true,
                    false,
                    "-----BEGIN PUBLIC KEY-----",
                    List.of("-----END PUBLIC KEY-----"))));

    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            withABlock,
            Map.of("apiKey", "-----BEGIN PUBLIC KEY-----\nMIIB\n-----END PUBLIC KEY-----"),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    AccountForm.Field pasted = theFieldNamed(asked, "apiKey");
    assertThat(pasted.isABlockOfManyLines())
        .as(
            "a value of many lines typed into a control that holds one line reaches the provider"
                + " as whatever the browser made of the newlines")
        .isTrue();
    assertThat(pasted.isTypedIn())
        .as("and the form draws one control for it rather than two")
        .isFalse();
    assertThat(pasted.isPickedFromAList()).isFalse();
    assertThat(pasted.isASpanOfTime()).isFalse();
    assertThat(pasted.value()).contains("\n");
    assertThat(pasted.theHintTheBoxShows()).isEqualTo("-----BEGIN PUBLIC KEY-----");
    assertThat(theFieldNamed(asked, "terminalId").isABlockOfManyLines())
        .as("and a box of one line stays one")
        .isFalse();
    assertThat(theFieldNamed(asked, "terminalId").isTypedIn()).isTrue();
  }

  @Test
  void a_refusal_the_screen_prints_names_the_box_a_tester_filled_in_and_not_the_request_property() {
    ConfigurationRefusal refusal =
        ConfigurationRefusal.of(
            "accounts[0].configuration.terminalId is required by provider aprovider");

    assertThat(
            refusedWith("terminalId is required by provider aprovider")
                .theRefusalAsTheBoxesOnThisScreenAreNamed(refusal))
        .as(
            "the box reads Terminal id on the screen; accounts[0].configuration is where the"
                + " value sits in the body the control API takes, and that same sentence is"
                + " answered verbatim to the suites that send it")
        .isEqualTo("Terminal id is required by provider aprovider");
  }

  @Test
  void a_refusal_of_the_identity_names_the_box_the_screen_asks_for_it_in() {
    ConfigurationRefusal refusal =
        ConfigurationRefusal.of("accounts[0].accountId is already configured by an earlier one");

    assertThat(
            refusedWith("terminalId is required").theRefusalAsTheBoxesOnThisScreenAreNamed(refusal))
        .as("the identity is a box of this screen's own, and the screen names it as it labels it")
        .isEqualTo("account identity is already configured by an earlier one");
  }

  @Test
  void a_refusal_that_names_nothing_a_box_of_this_screen_is_drawn_for_is_printed_as_it_stands() {
    AccountForm.View refused = refusedWith("terminalId is required");

    assertThat(
            refused.theRefusalAsTheBoxesOnThisScreenAreNamed(
                ConfigurationRefusal.of(
                    "no account of this test run answers to the name this form sent")))
        .as("a refusal of the whole form names no box, and nothing of it is rewritten")
        .isEqualTo("no account of this test run answers to the name this form sent");
    assertThat(
            refused.theRefusalAsTheBoxesOnThisScreenAreNamed(
                ConfigurationRefusal.of(
                    "accounts[0].configuration.aKeyThisProviderNeverDeclared is read by no"
                        + " account of provider aprovider")))
        .as(
            "and a key this provider declares no field for is drawn in no box either, so the"
                + " property it names is left as the only name there is for it")
        .isEqualTo(
            "accounts[0].configuration.aKeyThisProviderNeverDeclared is read by no account of"
                + " provider aprovider");
  }

  @Test
  void a_refusal_that_names_no_field_of_this_provider_marks_none_of_them() {
    AccountForm.View refused =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            ConfigurationRefusal.of("testRunId must match something else"));

    assertThat(refused.fields()).noneMatch(AccountForm.Field::refused);
  }

  @Test
  void the_identity_of_the_account_being_replaced_is_the_one_the_form_comes_back_holding() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.accountId()).isEqualTo("merchant-1");
    assertThat(asked.testRunId()).isEqualTo(RUN);
    assertThat(asked.provider()).isEqualTo("aprovider");
  }

  @Test
  void a_form_asked_for_before_an_account_exists_holds_no_identity_rather_than_a_null_one() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider", RUN, null, FIELDS, Map.of(), Map.of(), List.of(), null, List.of(), null);

    assertThat(asked.accountId()).isEmpty();
  }

  @Test
  void the_form_names_the_accounts_this_run_already_configures_so_a_second_one_can_be_added() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            null,
            FIELDS,
            Map.of(),
            Map.of(),
            List.of("merchant-1", "merchant-2"),
            null,
            List.of(),
            null);

    assertThat(asked.accountsAlreadyConfigured()).containsExactly("merchant-1", "merchant-2");
  }

  @Test
  void a_field_left_empty_is_not_sent_so_the_provider_answers_with_its_own_default() {
    Map<String, String> partial = new LinkedHashMap<>();
    partial.put("userId", "u");
    partial.put("terminalId", "7777");
    partial.put("captureWindow", "   ");

    DeclaredConfiguration asked =
        AccountForm.configurationOf(RUN, "merchant-1", FIELDS, partial, Map.of(), null);

    assertThat(theOnlyAccountOf(asked).configuration()).containsOnlyKeys("userId", "terminalId");
  }

  @Test
  void a_value_a_tester_typed_with_spaces_around_it_reaches_the_provider_without_them() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN, "  merchant-1  ", FIELDS, Map.of("userId", "  u  "), Map.of(), null);

    assertThat(theOnlyAccountOf(asked).accountId()).isEqualTo("merchant-1");
    assertThat(theOnlyAccountOf(asked).configuration()).containsEntry("userId", "u");
  }

  @Test
  void a_form_that_names_no_identity_leaves_the_identity_for_the_grammar_to_refuse() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(RUN, "   ", FIELDS, Map.of(), Map.of(), null);

    assertThat(theOnlyAccountOf(asked).accountId()).isNull();
  }

  @Test
  void a_value_sent_for_a_field_this_provider_never_declared_never_reaches_it() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN, "merchant-1", FIELDS, Map.of("userId", "u", "smuggled", "x"), Map.of(), null);

    assertThat(theOnlyAccountOf(asked).configuration()).containsOnlyKeys("userId");
  }

  @Test
  void the_account_form_configures_an_account_and_never_moves_the_clock_of_its_run() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN, "merchant-1", FIELDS, Map.of("userId", "u"), Map.of(), null);

    assertThat(asked.clock()).isNull();
    assertThat(asked.testRunId()).isEqualTo(RUN);
  }

  @Test
  void only_the_parameters_a_field_input_sent_are_read_as_configured_values() {
    Map<String, String> posted = new LinkedHashMap<>();
    posted.put("provider", "aprovider");
    posted.put("testRunId", RUN);
    posted.put("accountId", "merchant-1");
    posted.put("field.userId", "u");
    posted.put("field.azericard.partial-completion", "CLOSES_THE_AUTHORIZATION");

    assertThat(TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(posted))
        .containsOnly(
            Map.entry("userId", "u"),
            Map.entry("azericard.partial-completion", "CLOSES_THE_AUTHORIZATION"));
  }

  @Test
  void a_form_that_names_a_callback_target_configures_it_for_the_account_it_names() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN,
            "  merchant-1  ",
            FIELDS,
            Map.of("userId", "u"),
            Map.of(),
            "  https://merchant.example/callbacks  ");

    assertThat(asked.webhooks())
        .containsExactly(
            new DeclaredWebhookTarget("merchant-1", "https://merchant.example/callbacks"));
  }

  @Test
  void a_form_that_names_no_callback_target_sends_no_webhook_section_at_all() {
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN, "merchant-1", FIELDS, Map.of("userId", "u"), Map.of(), "   ");

    assertThat(asked.webhooks()).isNull();
  }

  @Test
  void a_callback_target_named_for_no_account_is_refused_before_the_grammar_sees_it() {
    assertThat(AccountForm.namesACallbackTargetForNoAccount("   ", "https://merchant.example/c"))
        .isTrue();
    assertThat(AccountForm.namesACallbackTargetForNoAccount(null, "https://merchant.example/c"))
        .isTrue();
    assertThat(AccountForm.namesACallbackTargetForNoAccount("merchant-1", "https://m.example/c"))
        .isFalse();
    assertThat(AccountForm.namesACallbackTargetForNoAccount(null, "   ")).isFalse();
  }

  @Test
  void a_form_drawn_before_a_callback_target_was_typed_comes_back_with_that_box_empty() {
    AccountForm.View asked =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            null);

    assertThat(asked.callbackUrl()).isEmpty();
  }

  @Test
  void a_form_refused_after_a_callback_target_was_typed_comes_back_holding_it() {
    AccountForm.View refused =
        AccountForm.of(
            "aprovider",
            RUN,
            "merchant-1",
            FIELDS,
            typed(),
            Map.of(),
            List.of(),
            "https://merchant.example/callbacks",
            List.of("https://merchant.example/callbacks"),
            ConfigurationRefusal.of("accounts[0].configuration.terminalId"));

    assertThat(refused.callbackUrl()).isEqualTo("https://merchant.example/callbacks");
    assertThat(refused.callbackTargetsAlreadyConfigured())
        .containsExactly("https://merchant.example/callbacks");
  }

  @Test
  void a_field_that_holds_a_span_of_time_is_stated_as_a_number_and_a_unit_and_never_typed_out() {
    AccountForm.Field span = theFieldNamed(aFormOn(Map.of()), "captureWindow");

    assertThat(span.isASpanOfTime()).isTrue();
    assertThat(span.isTypedIn())
        .as("a box here is the box that answered P7D with PT168H, which is what this replaces")
        .isFalse();
    assertThat(span.isPickedFromAList()).isFalse();
    assertThat(span.units())
        .extracting(ADurationInWords.Offered::whatItMeans)
        .containsExactly("seconds", "minutes", "hours", "days");
    assertThat(span.units())
        .extracting(ADurationInWords.Offered::value)
        .containsExactly("SECONDS", "MINUTES", "HOURS", "DAYS");
  }

  @Test
  void
      a_span_nobody_has_stated_yet_offers_the_unit_the_provider_counts_it_in_and_hints_its_number() {
    AccountForm.View asked = aFormOn(Map.of());

    assertThat(theChosenUnitOf(asked, "captureWindow"))
        .as("the provider publishes P7D, so a form that offered seconds would ask for 604800")
        .isEqualTo("DAYS");
    assertThat(theFieldNamed(asked, "captureWindow").value()).isEmpty();
    assertThat(theFieldNamed(asked, "captureWindow").theHintTheBoxShows()).isEqualTo("7");
    assertThat(theChosenUnitOf(asked, "webhookTimestampAge")).isEqualTo("MINUTES");
    assertThat(theFieldNamed(asked, "webhookTimestampAge").theHintTheBoxShows()).isEqualTo("6");
  }

  @Test
  void a_span_a_provider_counts_in_nothing_a_form_can_read_falls_back_on_the_unit_core_chooses() {
    AccountForm.View asked = aFormOn(Map.of());

    assertThat(theChosenUnitOf(asked, "settlementDelay"))
        .as(
            "the unit core falls back on is written out here rather than read from the constant"
                + " under test, which would agree with whatever that constant said")
        .isEqualTo("DAYS");
    assertThat(theFieldNamed(asked, "settlementDelay").theHintTheBoxShows())
        .as(
            "nothing a form can count is nothing a form can hint a number for, and the box is"
                + " left hinting nothing rather than hinting the key a tester would then type"
                + " into it")
        .isNull();
  }

  @Test
  void a_number_and_a_unit_reach_the_provider_as_the_one_span_the_two_of_them_name() {
    Map<String, String> posted = Map.of("field.captureWindow", "7", "unitOf.captureWindow", "DAYS");

    DeclaredConfiguration asked = theConfigurationPostedBy(posted);

    assertThat(theOnlyAccountOf(asked).configuration())
        .as(
            "the stored and transmitted form of this setting is unchanged by the control a"
                + " tester states it on; only the UI states it in words")
        .containsExactly(entry("captureWindow", "PT168H"));
  }

  @Test
  void a_span_a_refused_form_states_again_is_posted_as_the_span_it_was_posted_as_the_first_time() {
    Map<String, String> firstPost =
        Map.of("field.captureWindow", "7", "unitOf.captureWindow", "DAYS");
    String sentFirst =
        theOnlyAccountOf(theConfigurationPostedBy(firstPost)).configuration().get("captureWindow");

    AccountForm.View refused = theFormRefusedAfter(firstPost);
    Map<String, String> postedAgain =
        Map.of(
            "field.captureWindow",
            theFieldNamed(refused, "captureWindow").value(),
            "unitOf.captureWindow",
            theChosenUnitOf(refused, "captureWindow"));

    assertThat(theFieldNamed(refused, "captureWindow").value()).isEqualTo("7");
    assertThat(theChosenUnitOf(refused, "captureWindow")).isEqualTo("DAYS");
    assertThat(theOnlyAccountOf(theConfigurationPostedBy(postedAgain)).configuration())
        .as(
            "a form a tester submits again without touching the control must send the span it"
                + " sent the first time, and %s is what the provider was handed then",
            sentFirst)
        .containsExactly(entry("captureWindow", sentFirst));
  }

  @Test
  void a_span_a_call_wrote_is_read_into_the_number_and_unit_that_name_it_rather_than_a_box() {
    AccountForm.View refused = aFormOn(Map.of("captureWindow", "PT168H"));

    assertThat(theFieldNamed(refused, "captureWindow").value())
        .as(
            "a control call writes the ISO form, and a form refused after one states that span"
                + " as the number and unit a tester reads")
        .isEqualTo("7");
    assertThat(theChosenUnitOf(refused, "captureWindow")).isEqualTo("DAYS");
  }

  @Test
  void a_span_in_a_unit_of_its_own_comes_back_in_that_unit_rather_than_in_the_providers() {
    AccountForm.View refused = aFormOn(Map.of("captureWindow", "PT90M"));

    assertThat(theFieldNamed(refused, "captureWindow").value()).isEqualTo("90");
    assertThat(theChosenUnitOf(refused, "captureWindow")).isEqualTo("MINUTES");
  }

  @Test
  void a_value_no_number_and_unit_name_leaves_the_number_empty_and_the_unit_the_providers() {
    AccountForm.View refused = aFormOn(Map.of("captureWindow", "PT0.5S"));

    assertThat(theFieldNamed(refused, "captureWindow").value()).isEmpty();
    assertThat(theChosenUnitOf(refused, "captureWindow")).isEqualTo("DAYS");
  }

  @Test
  void a_number_that_is_no_number_comes_back_as_it_was_typed_and_travels_on_to_be_refused() {
    Map<String, String> posted =
        Map.of("field.captureWindow", "seven", "unitOf.captureWindow", "HOURS");

    AccountForm.View refused = theFormRefusedAfter(posted);

    assertThat(theFieldNamed(refused, "captureWindow").value())
        .as(
            "the control takes anything, so a tester who typed a word reads the word back"
                + " rather than an empty box they must guess at")
        .isEqualTo("seven");
    assertThat(theChosenUnitOf(refused, "captureWindow")).isEqualTo("HOURS");
    assertThat(theOnlyAccountOf(theConfigurationPostedBy(posted)).configuration())
        .as("the provider is the validator, so what it cannot read reaches it unchanged")
        .containsExactly(entry("captureWindow", "seven"));
  }

  @Test
  void a_unit_picked_for_a_field_that_holds_no_span_cannot_rewrite_what_was_typed_into_it() {
    Map<String, String> posted = Map.of("field.terminalId", "7", "unitOf.terminalId", "DAYS");

    AccountForm.View refused = theFormRefusedAfter(posted);

    assertThat(theOnlyAccountOf(theConfigurationPostedBy(posted)).configuration())
        .as(
            "no form sends this pair, and a hand-written one must not turn a terminal id into a"
                + " span of time: only a field the provider declares a duration is composed")
        .containsExactly(entry("terminalId", "7"));
    assertThat(theFieldNamed(refused, "terminalId").isTypedIn()).isTrue();
    assertThat(theFieldNamed(refused, "terminalId").value()).isEqualTo("7");
  }

  @Test
  void a_span_names_the_unit_it_is_counted_in_for_whoever_reads_the_control_without_seeing_it() {
    assertThat(theFieldNamed(aFormOn(Map.of()), "captureWindow").theUnitItIsCountedIn())
        .isEqualTo("the unit how long a hold is capturable is counted in");
  }

  @Test
  void a_field_that_is_no_span_of_time_sends_what_was_typed_into_it_exactly_as_it_was_typed() {
    Map<String, String> posted =
        Map.of("field.terminalId", " 7777 ", "unitOf.captureWindow", "DAYS");

    assertThat(theOnlyAccountOf(theConfigurationPostedBy(posted)).configuration())
        .as("a unit posted for one field is not a unit of another, and no box is composed here")
        .containsExactly(entry("terminalId", "7777"));
  }

  @Test
  void a_key_shaped_like_the_parameter_a_unit_travels_under_is_a_field_of_its_own_and_no_units() {
    ProviderAccountFields awkward =
        new ProviderAccountFields(
            ProviderId.of("aprovider"),
            List.of(
                ConfigurableField.aDuration(
                    "captureWindow",
                    "how long a hold lasts",
                    "a sentence a test does not read",
                    false,
                    "P7D"),
                ConfigurableField.aDuration(
                    "unitOf.captureWindow",
                    "how old a signed stamp is",
                    "another sentence a test does not read",
                    false,
                    "PT6M")));
    Map<String, String> parameters =
        Map.of(
            "field.captureWindow",
            "7",
            "unitOf.captureWindow",
            "DAYS",
            "field.unitOf.captureWindow",
            "6",
            "unitOf.unitOf.captureWindow",
            "MINUTES");

    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            RUN,
            "merchant-1",
            awkward,
            TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters),
            AccountForm.submittedUnitsOf(parameters),
            null);

    assertThat(theOnlyAccountOf(asked).configuration())
        .as(
            "a value and a unit travel under prefixes of their own, so a provider that declares"
                + " a key shaped like the unit parameter of another field takes neither that"
                + " field's number nor its unit")
        .containsOnly(entry("captureWindow", "PT168H"), entry("unitOf.captureWindow", "PT6M"));
  }

  private static DeclaredConfiguration theConfigurationPostedBy(Map<String, String> parameters) {
    return AccountForm.configurationOf(
        RUN,
        "merchant-1",
        SPANS,
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters),
        AccountForm.submittedUnitsOf(parameters),
        null);
  }

  private static AccountForm.View theFormRefusedAfter(Map<String, String> parameters) {
    return AccountForm.of(
        "aprovider",
        RUN,
        "merchant-1",
        SPANS,
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters),
        AccountForm.submittedUnitsOf(parameters),
        List.of(),
        null,
        List.of(),
        ConfigurationRefusal.of("accounts[0].configuration.terminalId"));
  }

  private static AccountForm.View aFormOn(Map<String, String> submitted) {
    return AccountForm.of(
        "aprovider",
        RUN,
        "merchant-1",
        SPANS,
        submitted,
        Map.of(),
        List.of(),
        null,
        List.of(),
        null);
  }

  private static String theChosenUnitOf(AccountForm.View asked, String key) {
    return theFieldNamed(asked, key).units().stream()
        .filter(ADurationInWords.Offered::chosen)
        .map(ADurationInWords.Offered::value)
        .reduce(
            (one, another) -> fail("one unit is chosen at a time, not " + one + " and " + another))
        .orElseThrow();
  }

  private static AccountForm.Field theFieldNamed(AccountForm.View asked, String key) {
    return asked.fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static AccountForm.View refusedWith(String property) {
    return AccountForm.of(
        "aprovider",
        RUN,
        "merchant-1",
        FIELDS,
        typed(),
        Map.of(),
        List.of(),
        null,
        List.of(),
        ConfigurationRefusal.of("accounts[0].configuration." + property));
  }

  private static Map<String, String> typed() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("userId", SECRET_TYPED);
    typed.put("terminalId", "7777");
    typed.put("captureWindow", "P3D");
    return typed;
  }

  private static DeclaredAccount theOnlyAccountOf(DeclaredConfiguration asked) {
    List<DeclaredAccount> accounts = asked.accounts();
    assertThat(accounts).hasSize(1);
    return accounts.get(0);
  }
}
