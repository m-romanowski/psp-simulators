package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GrowHostedPageTest {

  private static final TestRunId RUN = TestRunId.of("grow-hosted-page");

  @Test
  void the_url_it_hands_out_is_the_path_it_serves_and_carries_the_link_as_the_query() {
    String url = GrowHostedPage.urlFor("https://simulator.internal", "89a71a");

    assertThat(url).isEqualTo("https://simulator.internal/far?l=89a71a");
    assertThat(url).contains(GrowHostedPage.PATH);
  }

  @Test
  void a_link_that_needs_encoding_is_encoded_into_the_url() {
    assertThat(GrowHostedPage.urlFor("https://simulator.internal", "a b&c"))
        .isEqualTo("https://simulator.internal/far?l=a%20b%26c");
  }

  @Test
  void the_page_offers_every_card_of_the_run_and_every_outcome_a_payer_can_meet() {
    String page =
        GrowHostedPage.offering(
            GrowAuditFixtures.process(RUN, "734754"), GrowPayerCards.sandboxCards());

    assertThat(page).contains("sandbox-4580").contains("sandbox-0000").contains("sandbox-1121");
    for (PayerOutcome outcome : PayerOutcome.values()) {
      assertThat(page).contains("value=\"" + outcome.name() + "\"");
    }
    assertThat(page).contains("name=\"" + GrowHostedPage.CARD + "\"");
    assertThat(page).contains("name=\"" + GrowHostedPage.LINK + "\"");
  }

  @Test
  void the_page_carries_the_facts_the_payer_needs_to_recognise_what_is_being_charged() {
    String page = GrowHostedPage.offering(GrowAuditFixtures.process(RUN, "734754"), List.of());

    assertThat(page).contains("A hold").contains("120.50").contains("Israel Israeli");
  }

  @Test
  void the_page_and_its_refusal_are_documents_laid_out_for_a_phone() {
    String offered = GrowHostedPage.offering(GrowAuditFixtures.process(RUN, "734754"), List.of());
    String refused = GrowHostedPage.refusing(GrowHostedPage.NO_SUCH_LINK);

    for (String page : List.of(offered, refused)) {
      assertThat(page)
          .startsWith("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    }
  }

  @Test
  void the_page_carries_its_own_look_inside_its_own_bytes() {
    String offered = GrowHostedPage.offering(GrowAuditFixtures.process(RUN, "734754"), List.of());
    String refused = GrowHostedPage.refusing(GrowHostedPage.NO_SUCH_LINK);

    for (String page : List.of(offered, refused)) {
      assertThat(page).contains("<style>").contains("</style>");
      assertThat(page).contains("font:16px/1.5 system-ui,sans-serif");
      assertThat(page).contains("max-width:28rem").contains("margin:0 auto");
      assertThat(page).contains("<body><main>").contains("</main></body>");
      assertThat(page).contains("<h1>Pay</h1>");
      assertThat(page).contains("<title>");
      assertThat(page).doesNotContain("<link");
    }
    assertThat(refused).contains("<title>Pay</title>");
  }

  @Test
  void the_card_a_payer_names_is_asked_for_under_a_label_the_control_is_bound_to() {
    String page = GrowHostedPage.offering(GrowAuditFixtures.process(RUN, "734754"), List.of());

    assertThat(page)
        .contains("<label for=\"card\">Card</label>")
        .contains("<select id=\"card\" name=\"card\">");
  }

  @Test
  void only_the_step_that_names_a_card_is_the_pages_leading_button() {
    String page = GrowHostedPage.offering(GrowAuditFixtures.process(RUN, "734754"), List.of());

    assertThat(buttonFor(page, PayerOutcome.PAID))
        .as("paying is the step the page leads with")
        .doesNotContain("class=\"secondary\"");
    assertThat(buttonFor(page, PayerOutcome.DECLINED)).contains("class=\"secondary\"");
    assertThat(buttonFor(page, PayerOutcome.ABANDONED)).contains("class=\"secondary\"");
  }

  @Test
  void every_kind_of_control_the_page_emits_is_laid_out_by_its_own_stylesheet() {
    String page =
        GrowHostedPage.offering(
            GrowAuditFixtures.process(RUN, "734754"), GrowPayerCards.sandboxCards());
    String style = page.substring(page.indexOf("<style>"), page.indexOf("</style>"));

    assertThat(theKindsOfControlIn(page)).containsExactlyInAnyOrder("input", "select", "button");
    for (String control : theKindsOfControlIn(page)) {
      assertThat(style)
          .as("%s is laid out to the full width of the column", control)
          .containsPattern("[,{]?[a-z,]*\\b" + control + "\\b[a-z,]*\\{[^}]*width:100%");
      assertThat(style)
          .as("%s is given a target a thumb can hit", control)
          .containsPattern("[,{]?[a-z,]*\\b" + control + "\\b[a-z,]*\\{[^}]*min-height:44px");
    }
  }

  private static Set<String> theKindsOfControlIn(String page) {
    Set<String> kinds = new LinkedHashSet<>();
    Matcher controls = Pattern.compile("<(input|select|textarea|button)\\b").matcher(page);
    while (controls.find()) {
      kinds.add(controls.group(1));
    }
    return kinds;
  }

  private static String buttonFor(String page, PayerOutcome outcome) {
    int opens = page.indexOf("<button");
    while (opens >= 0) {
      int closes = page.indexOf("</button>", opens);
      String button = page.substring(opens, closes);
      if (button.contains("value=\"" + outcome.name() + "\"")) {
        return button;
      }
      opens = page.indexOf("<button", closes);
    }
    throw new AssertionError("the page offers no button for " + outcome);
  }

  @Test
  void merchant_text_reaches_the_page_escaped_rather_than_as_markup() {
    assertThat(GrowHostedPage.escaped("<script>&\"'")).isEqualTo("&lt;script&gt;&amp;&quot;&#39;");
    assertThat(GrowHostedPage.escaped("plain")).isEqualTo("plain");
  }

  @Test
  void a_payer_who_paid_goes_back_to_the_success_url_carrying_only_the_custom_fields() {
    String redirect =
        GrowHostedPage.redirectAfter(GrowAuditFixtures.process(RUN, "734754"), PayerOutcome.PAID);

    assertThat(redirect).startsWith("https://merchant.example/thanks?");
    assertThat(redirect).contains("cField1=session-abc").contains("cField2=intent-99");
    assertThat(redirect).doesNotContain("734754");
  }

  @Test
  void a_payer_who_did_not_pay_goes_back_to_the_cancel_url() {
    GrowPaymentProcess process = GrowAuditFixtures.process(RUN, "734754");

    assertThat(GrowHostedPage.redirectAfter(process, PayerOutcome.DECLINED))
        .startsWith("https://merchant.example/cancel?");
    assertThat(GrowHostedPage.redirectAfter(process, PayerOutcome.ABANDONED))
        .startsWith("https://merchant.example/cancel?");
  }

  @Test
  void a_return_url_that_already_carries_a_query_keeps_it() {
    GrowPaymentProcess process = withSuccessUrl("https://merchant.example/thanks?order=7");

    assertThat(GrowHostedPage.redirectAfter(process, PayerOutcome.PAID))
        .isEqualTo("https://merchant.example/thanks?order=7&cField1=session-abc&cField2=intent-99");
  }

  @Test
  void the_three_published_sandbox_cards_differ_only_in_the_suffix_grow_publishes() {
    List<GrowPayerCard> published = GrowPayerCards.sandboxCards();

    assertThat(published).hasSize(3);
    assertThat(published).extracting(GrowPayerCard::suffix).containsExactly("4580", "0000", "1121");
    assertThat(published)
        .allSatisfy(
            card -> {
              assertThat(card.brand()).isEqualTo(GrowSandboxCard.BRAND);
              assertThat(card.brandCode()).isEqualTo(GrowSandboxCard.BRAND_CODE);
              assertThat(card.type()).isEqualTo(GrowSandboxCard.TYPE);
              assertThat(card.typeCode()).isEqualTo(GrowSandboxCard.TYPE_CODE);
              assertThat(card.expiry()).isEqualTo(GrowSandboxCard.EXPIRY);
              assertThat(card.alias()).startsWith(GrowPayerCards.SANDBOX_PREFIX);
            });
  }

  @Test
  void a_card_is_described_to_a_payer_without_a_card_number() {
    assertThat(GrowPayerCards.sandboxCards().get(0).describedForAPayer())
        .isEqualTo("Visa " + GrowPayerCards.MASKED + "4580, 0424");
  }

  @Test
  void a_seeded_fixture_joins_the_cards_a_payer_may_name() {
    GrowPayerCards cards = new GrowPayerCards(seeded("my-card", "Mastercard", "1234", 4, 2031));

    assertThat(cards.resolve(RUN, "my-card")).isPresent();
    assertThat(cards.resolve(RUN, "my-card").orElseThrow().suffix()).isEqualTo("1234");
    assertThat(cards.aliasesOffered(RUN)).contains("my-card").contains("sandbox-4580");
  }

  @Test
  void a_fixture_card_is_mapped_onto_the_codes_grow_publishes() {
    GrowPayerCards cards = new GrowPayerCards(seeded("my-card", "Mastercard", "1234", 4, 2031));
    GrowPayerCard card = cards.resolve(RUN, "my-card").orElseThrow();

    assertThat(card.brandCode()).isEqualTo("2");
    assertThat(card.expiry()).isEqualTo("0431");
  }

  @Test
  void the_first_two_digit_month_is_the_one_a_padding_rule_gets_wrong() {
    GrowPayerCards cards = new GrowPayerCards(seeded("october", "Visa", "4444", 10, 2030));

    assertThat(cards.resolve(RUN, "october").orElseThrow().expiry()).isEqualTo("1030");
  }

  @Test
  void the_last_one_digit_month_is_padded() {
    GrowPayerCards cards = new GrowPayerCards(seeded("september", "Visa", "4444", 9, 2030));

    assertThat(cards.resolve(RUN, "september").orElseThrow().expiry()).isEqualTo("0930");
  }

  @Test
  void a_two_digit_month_is_not_padded_and_a_brand_grow_does_not_map_carries_no_code() {
    GrowPayerCards cards = new GrowPayerCards(seeded("other", "Maestro", "9999", 12, 2029));
    GrowPayerCard card = cards.resolve(RUN, "other").orElseThrow();

    assertThat(card.expiry()).isEqualTo("1229");
    assertThat(card.brandCode()).isEqualTo(GrowPayerCard.UNMAPPED);
    assertThat(card.brand()).isEqualTo("Maestro");
  }

  @Test
  void a_funding_grow_names_as_a_card_type_carries_the_published_code() {
    GrowPayerCards cards = new GrowPayerCards(seeded("debit", "Visa", "4444", 1, 2030, "Debit"));

    assertThat(cards.resolve(RUN, "debit").orElseThrow().typeCode()).isEqualTo("4");
  }

  @Test
  void a_card_no_fixture_and_no_published_test_card_names_resolves_to_nothing() {
    GrowPayerCards cards = new GrowPayerCards(new InMemoryPaymentMethodFixtures());

    assertThat(cards.resolve(RUN, "nothing-named-this")).isEmpty();
    assertThat(cards.offeredBy(RUN)).hasSize(3);
  }

  @Test
  void a_fixture_of_another_test_run_is_not_offered() {
    GrowPayerCards cards = new GrowPayerCards(seeded("my-card", "Visa", "1234", 4, 2031));

    assertThat(cards.resolve(TestRunId.of("another-run"), "my-card")).isEmpty();
  }

  private static GrowPaymentProcess withSuccessUrl(String successUrl) {
    GrowPaymentProcess process = GrowAuditFixtures.process(RUN, "734754");
    return new GrowPaymentProcess(
        process.testRunId(),
        process.processId(),
        process.processToken(),
        process.merchantId(),
        process.chargeType(),
        process.chargeSum(),
        process.description(),
        process.saveCardToken(),
        successUrl,
        process.cancelUrl(),
        process.notifyUrl(),
        process.payerFullName(),
        process.payerPhone(),
        process.payerEmail(),
        process.customFields(),
        process.hostedLink(),
        process.nativeStatusCode(),
        process.nativeStatus(),
        process.normalizedStatus(),
        process.outcome(),
        process.origin(),
        process.createdAt(),
        process.updatedAt());
  }

  @Test
  void the_policy_this_page_declares_names_the_floor_and_no_form_action() {
    assertThat(GrowHostedPage.WHAT_THIS_PAGE_PERMITS)
        .as(
            "a form on this page is posted here and answered with a redirect off this origin,"
                + " which is what grow does, and browsers disagree on whether form-action is"
                + " checked again across that hop")
        .isEqualTo("default-src 'none'; style-src 'unsafe-inline'");
    assertThat(GrowHostedPage.refusing(GrowHostedPage.NO_SUCH_LINK))
        .as("the page carries its own style, which is the one thing its policy permits")
        .contains("<style>");
  }

  private static InMemoryPaymentMethodFixtures seeded(
      String alias, String brand, String last4, int month, int year) {
    return seeded(alias, brand, last4, month, year, "Local");
  }

  private static InMemoryPaymentMethodFixtures seeded(
      String alias, String brand, String last4, int month, int year, String funding) {
    InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
    fixtures.seed(
        new NewPaymentMethodFixture(
            GrowLightApi.GROW,
            RUN,
            "merchant-1",
            "owner-1",
            PaymentMethodKind.CARD,
            alias,
            new FixtureCard(brand, last4, month, year, funding),
            Map.of(),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    return fixtures;
  }
}
