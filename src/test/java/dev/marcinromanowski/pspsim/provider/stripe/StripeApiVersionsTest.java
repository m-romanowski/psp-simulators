package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripeApiVersionsTest {

  private static final StripeApiVersion BASIL_OPENED = new StripeApiVersion("2025-03-31.basil");
  private static final StripeApiVersion THE_NEWEST = new StripeApiVersion("2026-10-28.endive");

  @Test
  void the_versions_answered_are_every_release_from_basil_to_endive_in_the_order_stripe_shipped() {
    assertThat(StripeApiVersions.answered())
        .extracting(StripeApiVersion::wireValue)
        .containsExactly(
            "2025-03-31.basil",
            "2025-04-30.basil",
            "2025-05-28.basil",
            "2025-06-30.basil",
            "2025-07-30.basil",
            "2025-08-27.basil",
            "2025-09-30.clover",
            "2025-10-29.clover",
            "2025-11-17.clover",
            "2025-12-15.clover",
            "2026-01-28.clover",
            "2026-02-25.clover",
            "2026-03-25.dahlia",
            "2026-04-22.dahlia",
            "2026-05-27.dahlia",
            "2026-06-24.dahlia",
            "2026-07-29.dahlia",
            "2026-08-26.dahlia",
            "2026-09-30.endive",
            "2026-10-28.endive");
    assertThat(StripeApiVersions.oldestAnswered()).isEqualTo(BASIL_OPENED);
    assertThat(StripeApiVersions.newest()).isEqualTo(THE_NEWEST);
    assertThat(StripeApiVersions.theRangeAnswered())
        .isEqualTo("2025-03-31.basil through 2026-10-28.endive");
  }

  @Test
  void acacia_is_a_published_major_this_simulator_does_not_answer() {
    assertThat(StripeApiMajor.ACACIA.answeredHere()).isFalse();
    assertThat(StripeApiMajor.ACACIA.releases())
        .extracting(StripeApiVersion::wireValue)
        .containsExactly(
            "2024-09-30.acacia",
            "2024-10-28.acacia",
            "2024-11-20.acacia",
            "2024-12-18.acacia",
            "2025-01-27.acacia",
            "2025-02-24.acacia");
    assertThat(StripeApiVersions.majorsAnswered())
        .containsExactly(
            StripeApiMajor.BASIL,
            StripeApiMajor.CLOVER,
            StripeApiMajor.DAHLIA,
            StripeApiMajor.ENDIVE);
    assertThat(StripeApiMajor.DAHLIA.opening().wireValue()).isEqualTo("2026-03-25.dahlia");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2011-06-21",
        "2020-03-02",
        "2020-08-27",
        "2024-06-20",
        "2024-09-30.acacia",
        "2025-02-24.acacia",
        "2025-04-30.preview",
        "2026-10-28.preview",
        "2025-03-31.basil",
        "2026-10-28.endive"
      })
  void every_version_the_changelog_names_is_published(String named) {
    assertThat(StripeApiVersions.isPublished(named)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-10-28",
        "2025-03-31.acacia",
        "2026-11-25.endive",
        "2025-03-31.preview",
        "2011-01-01",
        "latest",
        "2026-08-26.DAHLIA"
      })
  void a_value_the_changelog_never_names_is_not_published(String invented) {
    assertThat(StripeApiVersions.isPublished(invented)).isFalse();
  }

  @Test
  void the_published_lists_hold_the_count_of_releases_the_changelog_heads() {
    assertThat(StripeApiVersions.RELEASED_BEFORE_THE_FIRST_MAJOR)
        .hasSize(99)
        .startsWith("2011-06-21")
        .endsWith("2024-06-20")
        .doesNotHaveDuplicates()
        .isSorted();
    assertThat(StripeApiVersions.PREVIEW_RELEASE_DATES)
        .hasSize(19)
        .startsWith("2025-04-30")
        .endsWith("2026-10-28")
        .doesNotHaveDuplicates()
        .isSorted();
  }

  @Test
  void the_versions_the_mobile_sdks_pin_are_published_and_none_of_them_is_answered_by_default() {
    assertThat(StripeApiVersions.PINNED_BY_THE_MOBILE_SDKS)
        .extracting(StripeApiVersion::wireValue)
        .containsExactly("2020-03-02", "2020-08-27");
    for (StripeApiVersion pinned : StripeApiVersions.PINNED_BY_THE_MOBILE_SDKS) {
      assertThat(StripeApiVersions.isPublished(pinned.wireValue())).isTrue();
      assertThat(StripeApiVersions.answered()).doesNotContain(pinned);
    }
  }

  @Test
  void the_account_field_offers_every_version_answered_and_says_which_one_empty_gives() {
    ConfigurableField field =
        StripeAccount.ACCOUNT_FIELDS.field(StripeApiVersion.SETTING).orElseThrow();

    assertThat(field.required()).isFalse();
    assertThat(field.secret()).isFalse();
    assertThat(field.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactlyElementsOf(
            StripeApiVersions.answered().stream().map(StripeApiVersion::wireValue).toList());
    assertThat(field.whatItIsFor())
        .contains("sends no Stripe-Version header")
        .contains("webhook events")
        .contains(
            "Leaving it empty gives the account the newest version this simulator answers,"
                + " 2026-10-28.endive");
  }

  @Test
  void each_offered_version_says_whether_it_opened_a_major_or_only_added_to_one() {
    Map<String, String> sentences = new LinkedHashMap<>();
    for (AcceptedValue offered : StripeApiVersions.acceptedValues()) {
      sentences.put(offered.value(), offered.whatItMeans());
    }

    assertThat(sentences.get("2025-03-31.basil"))
        .isEqualTo(
            "a request sending no Stripe-Version, and every event, is answered at"
                + " 2025-03-31.basil, the release that opened basil and its breaks");
    assertThat(sentences.get("2026-08-26.dahlia"))
        .isEqualTo(
            "a request sending no Stripe-Version, and every event, is answered at"
                + " 2026-08-26.dahlia, a monthly dahlia release that adds to 2026-03-25.dahlia and"
                + " breaks nothing");
    assertThat(sentences.get("2026-10-28.endive"))
        .isEqualTo(
            "a request sending no Stripe-Version, and every event, is answered at"
                + " 2026-10-28.endive, the newest release, which an account leaving this empty is"
                + " given");
    assertThat(sentences.get("2026-09-30.endive"))
        .endsWith("the release that opened endive and its breaks");
  }

  @Test
  void an_account_leaving_its_default_empty_is_given_the_newest_version() {
    assertThat(StripeApiVersion.ofTheAccount("accounts[0].configuration.", null))
        .isEqualTo(THE_NEWEST);
    assertThat(StripeApiVersion.ofTheAccount("accounts[0].configuration.", " "))
        .isEqualTo(THE_NEWEST);
    assertThat(StripeApiVersion.ofTheAccount("accounts[0].configuration.", "2025-03-31.basil"))
        .isEqualTo(BASIL_OPENED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024-09-30.acacia", "2024-06-20", "2026-10-28.preview", "latest"})
  void an_account_default_this_simulator_does_not_answer_is_refused(String configured) {
    assertThatThrownBy(
            () -> StripeApiVersion.ofTheAccount("accounts[0].configuration.", configured))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[0].configuration.apiVersion must be one of the Stripe API versions this"
                + " simulator answers, 2025-03-31.basil through 2026-10-28.endive, written as"
                + " Stripe writes it, such as 2026-10-28.endive. An account's default is what a"
                + " request sending no Stripe-Version is answered at, so a version this simulator"
                + " does not answer would be a default no request could ever be answered at");
  }

  @Test
  void the_version_an_account_holds_is_kept_and_read_back_in_the_form_stripe_writes() {
    assertThat(StripeApiVersion.stored("2026-04-22.dahlia").storedForm())
        .isEqualTo("2026-04-22.dahlia");
  }

  @Test
  void a_stripe_version_header_is_read_from_the_first_non_blank_value_whatever_its_name_case() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer sk_test_x"));
    headers.put("stripe-VERSION", List.of(" ", " 2026-08-26.dahlia ", "2025-03-31.basil"));

    assertThat(StripeVersionChoice.sentIn(headers)).isEqualTo("2026-08-26.dahlia");
    assertThat(StripeVersionChoice.sentIn(Map.of("Authorization", List.of("x")))).isNull();
  }

  @Test
  void a_choice_falls_back_to_the_account_default_only_when_nothing_was_sent() {
    assertThat(StripeVersionChoice.forTheAccount(Map.of(), BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(BASIL_OPENED));
    assertThat(
            StripeVersionChoice.forTheAccount(
                Map.of("Stripe-Version", List.of("2026-10-28.endive")), BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(THE_NEWEST));
    assertThat(StripeVersionChoice.admittingTheMobileSdks(Map.of(), BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(BASIL_OPENED));
  }

  @Test
  void only_a_short_lived_key_is_answered_at_a_version_a_mobile_sdk_pins() {
    Map<String, List<String>> android = Map.of("Stripe-Version", List.of("2020-03-02"));

    assertThat(StripeVersionChoice.admittingTheMobileSdks(android, BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(new StripeApiVersion("2020-03-02")));
    assertThat(StripeVersionChoice.forTheAccount(android, BASIL_OPENED))
        .isInstanceOf(StripeVersionChoice.Refused.class);
    assertThat(
            StripeVersionChoice.admittingTheMobileSdks(
                Map.of("Stripe-Version", List.of("2024-09-30.acacia")), BASIL_OPENED))
        .isInstanceOf(StripeVersionChoice.Refused.class);
  }

  @Test
  void each_refusal_is_a_bad_request_of_the_invalid_request_type_with_no_code_and_no_param() {
    for (StripeError refused :
        List.of(
            StripeErrors.versionNotAnswered("2024-06-20"),
            StripeErrors.versionAsksForABeta("2025-03-31.basil; a_beta=v1"),
            StripeErrors.versionLaterThanTheCatalogue("2026-11-25.endive"),
            StripeErrors.versionUnrecognised("nope"))) {
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(refused.type()).isEqualTo(StripeErrorType.INVALID_REQUEST_ERROR);
      assertThat(refused.code()).isNull();
      assertThat(refused.param()).isNull();
    }
    assertThat(StripeErrors.versionNotAnswered("2024-06-20").message())
        .isEqualTo(
            "Stripe-Version 2024-06-20 is a published Stripe API version this simulator does not"
                + " answer. It answers 2025-03-31.basil through 2026-10-28.endive, every release"
                + " of basil, clover, dahlia and endive, because its behaviour carries the breaking"
                + " changes 2025-03-31.basil shipped. Send one of those, or send no Stripe-Version"
                + " to be answered at the account's default version.");
    assertThat(StripeErrors.versionAsksForABeta("2025-03-31.basil; a_beta=v1").message())
        .isEqualTo(
            "Stripe-Version 2025-03-31.basil; a_beta=v1 asks for a beta, and this simulator"
                + " answers no beta. It answers 2025-03-31.basil through 2026-10-28.endive, sent"
                + " without anything after the version.");
    assertThat(StripeErrors.versionLaterThanTheCatalogue("2026-11-25.endive").message())
        .isEqualTo(
            "Stripe-Version 2026-11-25.endive is dated after 2026-10-28.endive, the newest Stripe"
                + " API version this simulator was built with, so it may be a release Stripe"
                + " published since. This simulator does not answer it. It answers"
                + " 2025-03-31.basil through 2026-10-28.endive. Send one of those, or send no"
                + " Stripe-Version to be answered at the account's default version.");
    assertThat(StripeErrors.versionUnrecognised("nope").message())
        .isEqualTo("Invalid Stripe API version: nope");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-25.endive", "2027-03-31.fig", "2026-12-16.preview"})
  void a_dated_release_after_the_newest_answered_is_later_than_the_catalogue(String later) {
    assertThat(StripeApiVersions.isLaterThanTheCatalogue(later)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-10-28.endive",
        "2026-10-28.zinnia",
        "2026-10-29",
        "2026-02-30.endive",
        "2026-13-01.endive",
        "2026-11-25.Endive",
        "2026-11-25.endive; a_beta=v1",
        "x2026-11-25.endive"
      })
  void a_value_not_dated_after_the_newest_answered_is_not_later_than_the_catalogue(
      String notLater) {
    assertThat(StripeApiVersions.isLaterThanTheCatalogue(notLater)).isFalse();
  }

  @Test
  void the_versions_admitted_differ_only_in_the_versions_the_mobile_sdks_pin() {
    Map<String, List<String>> ios = Map.of("Stripe-Version", List.of("2020-08-27"));
    Map<String, List<String>> inRange = Map.of("Stripe-Version", List.of("2026-08-26.dahlia"));

    assertThat(StripeVersionsAdmitted.ALSO_THOSE_THE_MOBILE_SDKS_PIN.choose(ios, BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(new StripeApiVersion("2020-08-27")));
    assertThat(StripeVersionsAdmitted.THE_RANGE_ANSWERED.choose(ios, BASIL_OPENED))
        .isInstanceOf(StripeVersionChoice.Refused.class);
    assertThat(StripeVersionsAdmitted.THE_RANGE_ANSWERED.choose(inRange, BASIL_OPENED))
        .isEqualTo(new StripeVersionChoice.AnsweredAt(new StripeApiVersion("2026-08-26.dahlia")));
  }

  @Test
  void the_majors_a_refusal_names_read_as_prose_however_many_there_are() {
    assertThat(StripeErrors.inProse(List.of())).isEmpty();
    assertThat(StripeErrors.inProse(List.of("basil"))).isEqualTo("basil");
    assertThat(StripeErrors.inProse(List.of("basil", "clover"))).isEqualTo("basil and clover");
    assertThat(StripeErrors.inProse(List.of("basil", "clover", "dahlia", "endive")))
        .isEqualTo("basil, clover, dahlia and endive");
  }

  @Test
  void a_refusal_reads_a_beta_only_after_a_release_and_never_at_the_start_of_the_value() {
    assertThat(
            StripeVersionChoice.forTheAccount(
                Map.of("Stripe-Version", List.of("; 2025-03-31.basil")), BASIL_OPENED))
        .isEqualTo(
            new StripeVersionChoice.Refused(
                StripeErrors.versionUnrecognised("; 2025-03-31.basil")));
    assertThat(
            StripeVersionChoice.forTheAccount(
                Map.of("Stripe-Version", List.of("2025-03-31.basil ;a=v1;b=v2")), BASIL_OPENED))
        .isEqualTo(
            new StripeVersionChoice.Refused(
                StripeErrors.versionAsksForABeta("2025-03-31.basil ;a=v1;b=v2")));
  }

  @Test
  void a_beta_after_a_value_that_names_no_version_is_refused_as_an_invalid_version() {
    assertThat(
            StripeVersionChoice.forTheAccount(
                Map.of("Stripe-Version", List.of("nope; a_beta=v1")), BASIL_OPENED))
        .isEqualTo(
            new StripeVersionChoice.Refused(StripeErrors.versionUnrecognised("nope; a_beta=v1")));
  }
}
