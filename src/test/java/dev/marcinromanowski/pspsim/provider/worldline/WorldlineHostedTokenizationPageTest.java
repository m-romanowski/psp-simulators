package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldlineHostedTokenizationPageTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline-page");

  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-worldline-run");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static final String PUBLISHED_CARD = WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS;

  private static final String SEEDED_CARD = "seeded-mastercard";

  private final WorldlineDoubles.Merchants merchants = new WorldlineDoubles.Merchants();
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final WorldlineDoubles.HeldResponses held = new WorldlineDoubles.HeldResponses();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  private final SimulatorProvider provider = providerAt(WHEN);
  private final WorldlineHostedTokenizationCheckout checkout = checkoutOver(provider, WHEN);

  @Test
  void the_route_it_serves_is_the_path_the_hosted_tokenization_url_ends_with() {
    assertThat(WorldlineHostedTokenizationPage.ROUTE)
        .isEqualTo("/hostedtokenization/tokenization/form/{hostedTokenizationId}");
    assertThat(WorldlineHostedTokenizationPage.pathOf("abc"))
        .isEqualTo("/hostedtokenization/tokenization/form/abc");
  }

  @Test
  void the_page_offers_the_card_the_samples_print_and_every_card_seeded_into_the_run() {
    seedAMastercard(RUN);
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered page = checkout.open(id);

    assertThat(page.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.OFFERED);
    assertThat(page.body())
        .contains("<option value=\"" + PUBLISHED_CARD + "\">card ending 1111, expiring 1230")
        .contains("<option value=\"" + SEEDED_CARD + "\">MASTERCARD ending 4444, expiring 0131")
        .contains("<select id=\"card\" name=\"card\">")
        .contains("<label for=\"card\">" + WorldlineHostedTokenizationPage.CARD_LABEL + "</label>");
  }

  @Test
  void every_answer_of_this_page_carries_the_four_defences_in_one_order() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered offered = checkout.open(id);
    WorldlineHostedTokenizationCheckout.Rendered finished =
        checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);
    WorldlineHostedTokenizationCheckout.Rendered unknown = checkout.open("no-such-session");

    assertThat(List.of(offered.httpStatus(), finished.httpStatus(), unknown.httpStatus()))
        .as("three kinds of answer and not the same one three times")
        .containsExactly(
            WorldlineHostedTokenizationCheckout.OFFERED,
            WorldlineHostedTokenizationCheckout.FINISHED,
            WorldlineHostedTokenizationCheckout.NO_SUCH_SESSION);
    for (WorldlineHostedTokenizationCheckout.Rendered answer :
        List.of(offered, finished, unknown)) {
      assertThat(answer.headers())
          .as(
              "the answer carries the declaration, so deleting the one line the controller writes"
                  + " it with is the only thing left that no unit test can see")
          .containsExactly(
              Map.entry("Cache-Control", "no-store"),
              Map.entry("X-Content-Type-Options", "nosniff"),
              Map.entry("Referrer-Policy", "no-referrer"),
              Map.entry(
                  "Content-Security-Policy",
                  "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'"));
      assertThat(answer.headers().get("Content-Security-Policy"))
          .as("the shop frames this page on purpose, so it forbids no ancestor")
          .doesNotContain("frame-ancestors");
    }
  }

  @Test
  void the_page_offers_the_outcomes_worldline_allows_a_payer_and_no_decline() {
    WorldlineHostedTokenizationCheckout.Rendered page = checkout.open(aSessionIn(RUN));

    assertThat(page.body())
        .contains(
            "<button type=\"submit\" name=\"outcome\" value=\"PAID\">Store this card</button>")
        .contains(
            "<button type=\"submit\" class=\"secondary\" name=\"outcome\" value=\"ABANDONED\">"
                + "Leave without storing a card</button>")
        .doesNotContain("value=\"DECLINED\"");
  }

  @Test
  void every_step_this_page_draws_is_worded_by_worldline_and_never_by_this_simulators_wizard() {
    String page = checkout.open(aSessionIn(RUN)).body();

    for (PayerOutcome step : TokenizeOnTheHostedPage.THE_PAYER_STEPS.theStepsAPayerMayTake()) {
      assertThat(page)
          .as("a payer redirected here by a merchant's own app reads worldline's page")
          .contains("value=\"" + step.name() + "\">")
          .doesNotContain(TokenizeOnTheHostedPage.THE_PAYER_STEPS.asATesterReadsIt(step));
      assertThat(WorldlineHostedTokenizationPage.finished(step))
          .doesNotContain(TokenizeOnTheHostedPage.THE_PAYER_STEPS.asATesterReadsIt(step))
          .doesNotContain("null");
    }
    assertThat(page).doesNotContain(">null<");
  }

  @Test
  void the_page_posts_back_to_its_own_path_and_asks_for_no_card_number_and_no_credential() {
    String id = aSessionIn(RUN);

    String page = checkout.open(id).body();

    assertThat(page)
        .contains("<form method=\"post\" action=\"/hostedtokenization/tokenization/form/" + id)
        .doesNotContain("<input")
        .doesNotContain("key-" + WorldlineWire.ACCOUNT)
        .doesNotContain("secret-" + WorldlineWire.ACCOUNT);
  }

  @Test
  void the_page_is_one_self_contained_document_laid_out_for_a_phone() {
    String page = checkout.open(aSessionIn(RUN)).body();

    assertThat(page)
        .startsWith("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        .contains("<title>" + WorldlineHostedTokenizationPage.TITLE + "</title>")
        .doesNotContain("<script")
        .doesNotContain("<link")
        .doesNotContain("http://")
        .doesNotContain("https://");
  }

  @Test
  void a_payer_who_stores_a_card_leaves_the_session_as_the_control_completion_leaves_one() {
    String byThePage = aSessionIn(RUN);
    String byTheControlRoute = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(byThePage, PayerOutcome.PAID.name(), PUBLISHED_CARD);
    provider.complete(
        HostedCompletionRequest.of(
            provider.hostedCompletion(RUN, byTheControlRoute),
            RUN,
            byTheControlRoute,
            PayerOutcome.PAID,
            PUBLISHED_CARD,
            ObjectOrigin.CONTROL_API));

    WorldlineTokenization page = sessions.findById(RUN, byThePage).orElseThrow();
    WorldlineTokenization control = sessions.findById(RUN, byTheControlRoute).orElseThrow();
    assertThat(answered.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.FINISHED);
    assertThat(answered.body())
        .contains(
            WorldlineHostedTokenizationPage.WHAT_THE_PAYER_IS_TOLD_AFTERWARDS.get(
                PayerOutcome.PAID));
    assertThat(page.nativeStatus())
        .isEqualTo(control.nativeStatus())
        .isEqualTo(WorldlineStatus.TOKEN_CREATED);
    assertThat(page.cardNumber()).isEqualTo(control.cardNumber()).isEqualTo("************1111");
    assertThat(page.expiryDate()).isEqualTo(control.expiryDate()).isEqualTo("1230");
    assertThat(page.token()).matches("[0-9a-f]{32}").isNotEqualTo(control.token());
    assertThat(page.updatedAt()).isEqualTo(control.updatedAt());
  }

  @Test
  void an_outcome_padded_with_whitespace_is_accepted_as_the_control_route_accepts_it() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(id, "  " + PayerOutcome.PAID.name() + "  ", PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.FINISHED);
    assertThat(sessions.findById(RUN, id).orElseThrow().nativeStatus())
        .isEqualTo(WorldlineStatus.TOKEN_CREATED);
  }

  @Test
  void a_payer_who_stores_a_seeded_card_stores_the_token_that_fixture_was_seeded_with() {
    seedAMastercard(RUN);
    String id = aSessionIn(RUN);

    checkout.submit(id, PayerOutcome.PAID.name(), SEEDED_CARD);

    WorldlineTokenization session = sessions.findById(RUN, id).orElseThrow();
    assertThat(session.token()).isEqualTo("token-seeded");
    assertThat(session.cardNumber()).isEqualTo("************4444");
  }

  @Test
  void a_payer_who_leaves_ends_the_session_even_though_the_form_still_names_a_card() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(id, PayerOutcome.ABANDONED.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.FINISHED);
    assertThat(answered.body())
        .contains(
            WorldlineHostedTokenizationPage.WHAT_THE_PAYER_IS_TOLD_AFTERWARDS.get(
                PayerOutcome.ABANDONED));
    WorldlineTokenization session = sessions.findById(RUN, id).orElseThrow();
    assertThat(session.nativeStatus()).isEqualTo(WorldlineStatus.EXPIRED);
    assertThat(session.token()).isNull();
  }

  @Test
  void a_decline_posted_to_the_page_is_refused_before_the_provider_is_asked_to_complete() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout refusing =
        checkoutOver(offeringButRefusingToBeAskedToComplete(), WHEN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        refusing.submit(id, PayerOutcome.DECLINED.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body())
        .contains(
            WorldlineHostedTokenizationPage.escaped(
                TokenizeOnTheHostedPage.NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE));
    assertThat(stillWaiting(RUN, id)).isTrue();
  }

  @Test
  void an_outcome_no_payer_can_meet_is_refused_and_changes_nothing() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(id, "PAY", PUBLISHED_CARD);
    WorldlineHostedTokenizationCheckout.Rendered none = checkout.submit(id, null, PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body()).contains("outcome must be one of PAID, DECLINED, ABANDONED");
    assertThat(none.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(stillWaiting(RUN, id)).isTrue();
  }

  @Test
  void a_card_this_run_does_not_offer_is_refused_and_changes_nothing() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(id, PayerOutcome.PAID.name(), "4111111111111111");

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body()).contains("does not offer in this test run");
    assertThat(stillWaiting(RUN, id)).isTrue();
  }

  @Test
  void a_store_that_names_no_card_is_refused_and_changes_nothing() {
    String id = aSessionIn(RUN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        checkout.submit(id, PayerOutcome.PAID.name(), null);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body())
        .contains(
            WorldlineHostedTokenizationPage.escaped(HostedCompletionRequest.A_CARD_IS_REQUIRED));
    assertThat(stillWaiting(RUN, id)).isTrue();
  }

  @Test
  void a_session_no_run_holds_is_refused_on_both_verbs() {
    String unknown = "0123456789abcdef0123456789abcdef";

    WorldlineHostedTokenizationCheckout.Rendered opened = checkout.open(unknown);
    WorldlineHostedTokenizationCheckout.Rendered submitted =
        checkout.submit(unknown, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(opened.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.NO_SUCH_SESSION);
    assertThat(submitted.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_SUCH_SESSION);
    assertThat(opened.body())
        .contains(WorldlineHostedTokenizationPage.NO_SUCH_SESSION)
        .doesNotContain("<form");
    assertThat(audit.all()).isEmpty();
  }

  @Test
  void a_finished_session_is_refused_on_both_verbs_and_keeps_the_token_it_stored() {
    String id = aSessionIn(RUN);
    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);
    String stored = sessions.findById(RUN, id).orElseThrow().token();

    WorldlineHostedTokenizationCheckout.Rendered opened = checkout.open(id);
    WorldlineHostedTokenizationCheckout.Rendered again =
        checkout.submit(id, PayerOutcome.ABANDONED.name(), PUBLISHED_CARD);

    assertThat(opened.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING);
    assertThat(opened.body()).contains("has already tokenized a card").doesNotContain("<form");
    assertThat(again.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING);
    WorldlineTokenization session = sessions.findById(RUN, id).orElseThrow();
    assertThat(session.token()).isEqualTo(stored);
    assertThat(session.nativeStatus()).isEqualTo(WorldlineStatus.TOKEN_CREATED);
  }

  @Test
  void a_session_past_the_published_three_hours_is_refused_on_both_verbs() {
    String id = aSessionIn(RUN);
    Instant later = WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN);
    WorldlineHostedTokenizationCheckout late = checkoutOver(providerAt(later), later);

    WorldlineHostedTokenizationCheckout.Rendered opened = late.open(id);
    WorldlineHostedTokenizationCheckout.Rendered submitted =
        late.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(opened.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING);
    assertThat(opened.body()).contains("past the three hours");
    assertThat(submitted.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING);
    assertThat(sessions.findById(RUN, id).orElseThrow().token()).isNull();
  }

  @Test
  void the_page_finds_its_session_in_whichever_run_opened_it_without_being_told_the_run() {
    String id = aSessionIn(ANOTHER_RUN);

    WorldlineHostedTokenizationCheckout.Rendered opened = checkout.open(id);
    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(opened.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.OFFERED);
    assertThat(sessions.findById(ANOTHER_RUN, id).orElseThrow().nativeStatus())
        .isEqualTo(WorldlineStatus.TOKEN_CREATED);
    assertThat(sessions.findById(RUN, id)).isEmpty();
  }

  @Test
  void no_second_run_can_hold_a_session_under_an_identifier_another_run_already_holds() {
    String id = aSessionIn(RUN);
    WorldlineTokenization first = sessions.findById(RUN, id).orElseThrow();

    boolean inserted =
        sessions.insertIfAbsent(
            new WorldlineTokenization(
                ANOTHER_RUN,
                id,
                first.accountId(),
                first.merchantId(),
                WorldlineStatus.AWAITING_THE_PAYER,
                null,
                null,
                null,
                first.expiresAt(),
                ObjectOrigin.PROVIDER_API,
                WHEN,
                WHEN));

    assertThat(inserted).isFalse();
    assertThat(sessions.findByIdInAnyRun(id).orElseThrow().testRunId()).isEqualTo(RUN);
  }

  @Test
  void a_submission_is_in_the_trail_of_the_run_that_opened_the_session_as_the_page_exchange() {
    String id = aSessionIn(ANOTHER_RUN);

    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, ANOTHER_RUN);
    assertThat(trail).hasSize(2);
    AuditRecord inbound = trail.get(0);
    AuditRecord outbound = trail.get(1);
    assertThat(inbound.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(inbound.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(inbound.method()).isEqualTo("POST");
    assertThat(inbound.path()).isEqualTo(WorldlineHostedTokenizationPage.pathOf(id));
    assertThat(inbound.accountId()).isEqualTo(WorldlineWire.ACCOUNT);
    assertThat(inbound.createdAt()).isEqualTo(WHEN);
    assertThat(inbound.auditId()).startsWith("aud_").hasSize(28);
    assertThat(inbound.sanitizedBody())
        .containsEntry(WorldlineHostedTokenizationPage.OUTCOME, List.of("PAID"))
        .containsEntry(WorldlineHostedTokenizationPage.CARD, List.of(PUBLISHED_CARD));
    assertThat(outbound.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(WorldlineHostedTokenizationCheckout.FINISHED);
    assertThat(outbound.providerObjectId()).isEqualTo(id);
    assertThat(outbound.refusal()).isNull();
    assertThat(outbound.path()).isEqualTo(WorldlineHostedTokenizationPage.pathOf(id));
  }

  @Test
  void a_refused_submission_is_in_the_trail_as_a_failure_naming_the_session() {
    String id = aSessionIn(RUN);

    checkout.submit(id, "PAY", null);

    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, RUN);
    assertThat(trail).hasSize(2);
    assertThat(trail.get(0).sanitizedBody())
        .containsEntry(WorldlineHostedTokenizationPage.OUTCOME, List.of("PAY"))
        .doesNotContainKey(WorldlineHostedTokenizationPage.CARD);
    assertThat(trail.get(1).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(trail.get(1).httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(trail.get(1).providerObjectId()).isEqualTo(id);
    assertThat(trail.get(1).refusal()).isNotNull();
  }

  @Test
  void a_submission_to_a_finished_session_is_in_the_trail_as_a_failure() {
    String id = aSessionIn(RUN);
    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    checkout.submit(id, null, null);

    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, RUN);
    assertThat(trail).hasSize(4);
    assertThat(trail.get(2).sanitizedBody())
        .containsEntry(WorldlineHostedTokenizationPage.OUTCOME, List.of(""));
    assertThat(trail.get(3).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(trail.get(3).httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING);
  }

  @Test
  void a_completion_the_provider_no_longer_offers_is_answered_in_the_providers_words() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout answering =
        checkoutOver(
            completing(new HostedCompletionOutcome.NotOffered("worldline has no <page> here")),
            WHEN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        answering.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body()).contains("worldline has no &lt;page&gt; here");
    assertThat(audit.of(WorldlineApi.WORLDLINE, RUN).get(1).outcome())
        .isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_completion_the_provider_refuses_while_the_session_still_waits_is_422_in_its_own_words() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout refusing =
        checkoutOver(
            completing(
                new HostedCompletionOutcome.RefusedByTheProvider(
                    "worldline stores no card under <that> alias")),
            WHEN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        refusing.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
    assertThat(answered.body()).contains("worldline stores no card under &lt;that&gt; alias");
    assertThat(stillWaiting(RUN, id)).isTrue();
    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, RUN);
    assertThat(trail).hasSize(2);
    assertThat(trail.get(1).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(trail.get(1).httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.REFUSED)
        .isEqualTo(422);
  }

  @Test
  void a_completion_refused_because_another_request_already_finished_the_session_is_410() {
    String id = aSessionIn(RUN);
    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);
    WorldlineHostedTokenizationCheckout racing =
        checkoutOver(
            completing(
                new HostedCompletionOutcome.RefusedByTheProvider(
                    "a different request already tokenized this session")),
            WHEN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        racing.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING)
        .isEqualTo(410);
    assertThat(answered.body()).contains("has already tokenized a card");
    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, RUN);
    assertThat(trail).hasSize(4);
    assertThat(trail.get(3).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(trail.get(3).httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_LONGER_WAITING)
        .isEqualTo(410);
  }

  @Test
  void a_session_reset_between_the_page_and_the_completion_is_answered_as_unknown() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout answering =
        checkoutOver(completing(new HostedCompletionOutcome.NoSuchObject()), WHEN);

    WorldlineHostedTokenizationCheckout.Rendered answered =
        answering.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    assertThat(answered.httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_SUCH_SESSION);
    assertThat(answered.body()).contains(WorldlineHostedTokenizationPage.NO_SUCH_SESSION);
    List<AuditRecord> trail = audit.of(WorldlineApi.WORLDLINE, RUN);
    assertThat(trail.get(1).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(trail.get(1).httpStatus())
        .isEqualTo(WorldlineHostedTokenizationCheckout.NO_SUCH_SESSION);
  }

  @Test
  void text_reaches_the_page_escaped_rather_than_as_markup() {
    assertThat(WorldlineHostedTokenizationPage.escaped("<script>&\"'"))
        .isEqualTo("&lt;script&gt;&amp;&quot;&#39;");
    assertThat(WorldlineHostedTokenizationPage.refusing("<b>")).contains("<p>&lt;b&gt;</p>");
  }

  @Test
  void a_card_label_a_fixture_carries_reaches_the_page_escaped() {
    String page =
        WorldlineHostedTokenizationPage.offering(
            "abc",
            new HostedCompletionOffer.Offered(
                WorldlineApi.WORLDLINE,
                List.of(new PayerCard("a\"b", "<i>card</i>")),
                TokenizeOnTheHostedPage.THE_PAYER_STEPS));

    assertThat(page).contains("<option value=\"a&quot;b\">&lt;i&gt;card&lt;/i&gt;</option>");
  }

  @Test
  void opening_an_offer_that_names_no_card_fails_loudly_rather_than_reading_as_410() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout unrenderable = checkoutOver(offeringWithoutACard(), WHEN);

    assertThatThrownBy(() -> unrenderable.open(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(WorldlineHostedTokenizationCheckout.AN_OFFER_THIS_PAGE_CANNOT_RENDER);
  }

  @Test
  void submitting_to_an_offer_that_names_no_card_fails_loudly_rather_than_reading_as_410() {
    String id = aSessionIn(RUN);
    WorldlineHostedTokenizationCheckout unrenderable = checkoutOver(offeringWithoutACard(), WHEN);

    assertThatThrownBy(() -> unrenderable.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(WorldlineHostedTokenizationCheckout.AN_OFFER_THIS_PAGE_CANNOT_RENDER);
  }

  private boolean stillWaiting(TestRunId run, String id) {
    WorldlineTokenization session = sessions.findById(run, id).orElseThrow();
    return session.isAwaitingThePayer() && session.token() == null;
  }

  private String aSessionIn(TestRunId run) {
    WorldlineOperationResult opened =
        new CreateHostedTokenization(
                sessions,
                new WorldlineAnswers(ids),
                ids,
                new ScenarioEngine(rules),
                WorldlineWire.PUBLIC_BASE_URL)
            .handle(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{}"),
                Map.of(),
                run,
                WorldlineDoubles.merchant(run, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT),
                WHEN,
                ObjectOrigin.PROVIDER_API);
    return String.valueOf(opened.providerObjectId());
  }

  private void seedAMastercard(TestRunId run) {
    fixtures.seed(
        new NewPaymentMethodFixture(
            WorldlineApi.WORLDLINE,
            run,
            WorldlineWire.MERCHANT,
            "owner-1",
            PaymentMethodKind.CARD,
            SEEDED_CARD,
            new FixtureCard("MASTERCARD", "4444", 1, 2031, "credit"),
            Map.of(ProviderPaymentMethodFields.TOKEN, "token-seeded"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
  }

  private WorldlineHostedTokenizationCheckout checkoutOver(
      SimulatorProvider answering, Instant now) {
    SimulatorClock clock = WorldlineDoubles.clockAt(now);
    return new WorldlineHostedTokenizationCheckout(sessions, answering, audit, clock, ids);
  }

  private SimulatorProvider providerAt(Instant now) {
    ScenarioEngine scenarios = new ScenarioEngine(rules);
    WorldlineAnswers answers = new WorldlineAnswers(ids);
    WorldlinePayerCards cards = new WorldlinePayerCards(fixtures);
    return new WorldlineProvider(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        fixtures,
        rules,
        held,
        WorldlineDoubles.clockAt(now),
        new CreateHostedTokenization(
            sessions, answers, ids, scenarios, WorldlineWire.PUBLIC_BASE_URL),
        new CreatePayment(payments, sessions, cards, keys, answers, ids, scenarios),
        new CapturePayment(payments, answers, keys, scenarios),
        new CancelPayment(payments, answers, keys, scenarios),
        new TokenizeOnTheHostedPage(sessions, cards, ids),
        ids);
  }

  private SimulatorProvider completing(HostedCompletionOutcome outcome) {
    HostedCompletionOffer offer =
        new HostedCompletionOffer.Offered(
            WorldlineApi.WORLDLINE,
            List.of(new PayerCard(PUBLISHED_CARD, "card ending 1111")),
            TokenizeOnTheHostedPage.THE_PAYER_STEPS);
    return (SimulatorProvider)
        Proxy.newProxyInstance(
            SimulatorProvider.class.getClassLoader(),
            new Class<?>[] {SimulatorProvider.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "hostedCompletion" -> offer;
                  case "complete" -> outcome;
                  default -> throw new AssertionError(method.getName() + " was not expected");
                });
  }

  private SimulatorProvider offeringButRefusingToBeAskedToComplete() {
    HostedCompletionOffer offer =
        new HostedCompletionOffer.Offered(
            WorldlineApi.WORLDLINE,
            List.of(new PayerCard(PUBLISHED_CARD, "card ending 1111")),
            TokenizeOnTheHostedPage.THE_PAYER_STEPS);
    return (SimulatorProvider)
        Proxy.newProxyInstance(
            SimulatorProvider.class.getClassLoader(),
            new Class<?>[] {SimulatorProvider.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "hostedCompletion" -> offer;
                  default ->
                      throw new AssertionError(
                          "the page asked provider worldline to "
                              + method.getName()
                              + " a payer step its own declaration does not offer");
                });
  }

  private SimulatorProvider offeringWithoutACard() {
    HostedCompletionOffer offer =
        new HostedCompletionOffer.OfferedWithoutACard(
            WorldlineApi.WORLDLINE,
            "a shape this simulator's own worldline module never offers",
            TokenizeOnTheHostedPage.THE_PAYER_STEPS);
    return (SimulatorProvider)
        Proxy.newProxyInstance(
            SimulatorProvider.class.getClassLoader(),
            new Class<?>[] {SimulatorProvider.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "hostedCompletion" -> offer;
                  default -> throw new AssertionError(method.getName() + " was not expected");
                });
  }

  @Test
  void the_row_this_page_answers_with_carries_no_field_at_all() {
    String id = aSessionIn(ANOTHER_RUN);

    checkout.submit(id, PayerOutcome.PAID.name(), PUBLISHED_CARD);

    AuditRecord answered = audit.of(WorldlineApi.WORLDLINE, ANOTHER_RUN).get(1);
    assertThat(answered.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(answered.sanitizedBody())
        .as(
            "this page answers a browser with a document, so worldline's declaration is applied"
                + " to an empty body here and this row is the same row it was before it was")
        .isEmpty();
  }
}
