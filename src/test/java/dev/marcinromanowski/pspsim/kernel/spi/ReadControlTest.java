package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadControlTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("read-control");

  private static final String A_RETRACE = "aRetrace";

  private static final String ACCOUNT = "merchant-1";

  private static final AValueAFieldOffers THE_CARD_THIS_RUN_SAVED =
      new AValueAFieldOffers("tok_1", "the card Dana Levi saved on obj_1");

  private static final AFieldATesterFillsIn THE_SAVED_CARD =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          "cardToken", "the saved card the charge was taken on", true, "what this run minted.");

  private static final AFieldATesterFillsIn MY_OWN_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          "myOwnIdentifier",
          "your own identifier for the charge",
          true,
          "what this provider finds the charge by.",
          "1001");

  private static final AFieldATesterFillsIn MY_OWN_GROUP =
      AFieldATesterFillsIn.typed(
          "myOwnGroup", "your own identifier for the group", false, "a second one of yours.", null);

  private static final AFieldATesterFillsIn A_CLOSED_SET =
      AFieldATesterFillsIn.chosenFrom(
          "kind",
          "the kind of charge",
          false,
          "which of these this provider answers for.",
          List.of(new AValueAFieldOffers("1", "a regular one")));

  private static final String IT_PUBLISHES_NONE =
      "this provider publishes every read on its own object addresses and takes no identifier of"
          + " a merchant's own anywhere";

  private static final ProviderReads PUBLISHES_NONE =
      ProviderReads.noneOfThem(APROVIDER, IT_PUBLISHES_NONE);

  private static final ProviderReads DECLARED =
      ProviderReads.theOnesItPublishes(
          APROVIDER,
          List.of(
              new DeclaredRead(
                  A_RETRACE,
                  "Find the charge",
                  "which charge it holds under a merchant's own identifiers.",
                  DeclaredRead.EmptyAnswer.whatItMeans(
                      "it holds no charge of yours under those identifiers."),
                  List.of(THE_SAVED_CARD, MY_OWN_IDENTIFIER, MY_OWN_GROUP, A_CLOSED_SET))));

  @Test
  void a_read_asked_with_the_values_a_tester_filled_in_names_the_account_and_the_question() {
    ReadRequest request =
        ReadControl.asked(
            DECLARED,
            RUN,
            ACCOUNT,
            A_RETRACE,
            submitted("cardToken", "tok_1", "myOwnIdentifier", "1001"),
            ObjectOrigin.UI);

    assertThat(request.accountId()).isEqualTo(ACCOUNT);
    assertThat(request.readNamed()).isEqualTo(A_RETRACE);
    assertThat(request.values()).containsEntry("cardToken", "tok_1");
    assertThat(request.required("myOwnIdentifier")).isEqualTo("1001");
    assertThat(request.value("myOwnGroup")).isEmpty();
  }

  @Test
  void a_value_left_empty_is_sent_as_nothing_rather_than_as_an_empty_one() {
    ReadRequest request =
        ReadControl.asked(
            DECLARED,
            RUN,
            ACCOUNT,
            A_RETRACE,
            submitted("cardToken", "tok_1", "myOwnIdentifier", "1001", "myOwnGroup", "  "),
            ObjectOrigin.UI);

    assertThat(request.values()).doesNotContainKey("myOwnGroup");
  }

  @Test
  void the_values_a_read_carries_are_in_the_order_the_read_declares_its_fields() {
    ReadRequest request =
        ReadControl.asked(
            DECLARED,
            RUN,
            ACCOUNT,
            A_RETRACE,
            submitted("myOwnGroup", "group-1", "myOwnIdentifier", "1001", "cardToken", "tok_1"),
            ObjectOrigin.UI);

    assertThat(request.values().keySet())
        .as(
            "a provider reads these onto its own wire by iterating them, so the order is the one"
                + " the provider declared its fields in and never the order a browser happened to"
                + " send them in or a hash map happened to hold them in")
        .containsExactly("cardToken", "myOwnIdentifier", "myOwnGroup");
  }

  @Test
  void a_read_asked_on_no_account_is_refused_before_the_provider_is_reached() {
    assertThatThrownBy(
            () -> ReadControl.asked(DECLARED, RUN, " ", A_RETRACE, Map.of(), ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ReadControl.AN_ACCOUNT_IS_REQUIRED);
  }

  @Test
  void a_question_asked_of_a_provider_that_publishes_none_is_refused_with_what_it_has_instead() {
    assertThatThrownBy(
            () ->
                ReadControl.asked(
                    PUBLISHES_NONE, RUN, ACCOUNT, A_RETRACE, Map.of(), ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "provider aprovider answers no question a tester asks with identifiers of their own,"
                + " so there is no "
                + A_RETRACE
                + " to ask of it and no other question of it either: "
                + IT_PUBLISHES_NONE);
  }

  @Test
  void a_question_this_provider_does_not_publish_is_refused_with_the_ones_it_does() {
    assertThatThrownBy(
            () ->
                ReadControl.asked(
                    DECLARED, RUN, ACCOUNT, "somethingElse", Map.of(), ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("answers one of these questions and no other: Find the charge");
  }

  @Test
  void a_read_missing_a_value_it_cannot_be_answered_without_is_refused_in_the_providers_words() {
    assertThatThrownBy(
            () ->
                ReadControl.asked(
                    DECLARED,
                    RUN,
                    ACCOUNT,
                    A_RETRACE,
                    submitted("cardToken", "tok_1"),
                    ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be answered without your own identifier for the charge")
        .hasMessageContaining("what this provider finds the charge by.");
  }

  @Test
  void a_value_sent_for_a_field_this_read_does_not_declare_is_refused_rather_than_dropped() {
    assertThatThrownBy(
            () ->
                ReadControl.asked(
                    DECLARED,
                    RUN,
                    ACCOUNT,
                    A_RETRACE,
                    submitted("cardToken", "tok_1", "myOwnIdentifier", "1001", "sum", "10.00"),
                    ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nothing is sent for sum");
  }

  @Test
  void a_value_outside_the_set_this_provider_publishes_is_left_for_the_provider_to_refuse() {
    ReadRequest request =
        ReadControl.asked(
            DECLARED,
            RUN,
            ACCOUNT,
            A_RETRACE,
            submitted("cardToken", "tok_1", "myOwnIdentifier", "1001", "kind", "9"),
            ObjectOrigin.UI);

    assertThat(request.value("kind"))
        .as(
            "the values a provider declares are the ones it publishes, which this screen offers"
                + " and never imposes: autra answers 400 INVALID_STATUS and 400 INVALID_TYPE for"
                + " a value it does not publish, and a read this core refused in its own words"
                + " would hide that refusal from the only screen it was written for")
        .contains("9");
  }

  @Test
  void a_token_this_run_never_minted_is_left_for_the_provider_to_refuse() {
    ReadRequest request =
        ReadControl.asked(
            DECLARED,
            RUN,
            ACCOUNT,
            A_RETRACE,
            submitted("cardToken", "tok_nobody_saved", "myOwnIdentifier", "1001"),
            ObjectOrigin.UI);

    assertThat(request.value("cardToken"))
        .as(
            "the provider is the validator of a token it minted itself, and its own refusal is"
                + " what a tester came to this screen to read")
        .contains("tok_nobody_saved");
  }

  @Test
  void the_values_this_run_produced_are_offered_for_every_field_that_names_them() {
    Map<String, List<AValueAFieldOffers>> produced =
        ReadControl.theValuesThisTestRunHasAlreadyProduced(
            DECLARED, ACCOUNT, A_RETRACE, (account, key) -> theCardsOf(account, key));

    assertThat(produced).containsOnlyKeys("cardToken");
    assertThat(produced.get("cardToken")).containsExactly(THE_CARD_THIS_RUN_SAVED);
  }

  @Test
  void no_value_is_asked_of_a_provider_before_an_account_and_a_question_are_named() {
    assertThat(
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                DECLARED, null, A_RETRACE, (account, key) -> theCardsOf(account, key)))
        .isEmpty();
    assertThat(
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                DECLARED, " ", A_RETRACE, (account, key) -> theCardsOf(account, key)))
        .isEmpty();
    assertThat(
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                DECLARED, ACCOUNT, null, (account, key) -> theCardsOf(account, key)))
        .isEmpty();
    assertThat(
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                DECLARED, ACCOUNT, "somethingElse", (account, key) -> theCardsOf(account, key)))
        .isEmpty();
  }

  private static List<AValueAFieldOffers> theCardsOf(String accountId, String fieldKey) {
    List<AValueAFieldOffers> offered = new ArrayList<>();
    if (ACCOUNT.equals(accountId) && "cardToken".equals(fieldKey)) {
      offered.add(THE_CARD_THIS_RUN_SAVED);
    }
    return List.copyOf(offered);
  }

  private static Map<String, String> submitted(String... namesAndValues) {
    Map<String, String> values = new LinkedHashMap<>();
    for (int at = 0; at < namesAndValues.length; at += 2) {
      values.put(namesAndValues[at], namesAndValues[at + 1]);
    }
    return values;
  }
}
