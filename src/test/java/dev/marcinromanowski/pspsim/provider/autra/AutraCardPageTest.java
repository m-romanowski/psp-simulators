package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionControl;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AutraCardPageTest {

  private static final TestRunId RUN = TestRunId.of("autra-card-page");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("autra-card-page-elsewhere");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String A_SEEDED_CARD = "a-seeded-card";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.CardSessions cardSessions = new AutraDoubles.CardSessions();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final IdGenerator ids = new AutraDoubles.CountingIds();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

  private final WebhookDispatcher callbackDeliveries =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          testRunId -> now.get(),
          new AutraDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(rules),
          List.of(new AutraCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AutraCallbacks callbacks =
      new AutraCallbacks(
          pixPayments,
          merchants,
          new AutraDoubles.Accounts(),
          callbackDeliveries,
          new AutraDoubles.CountingIds());

  private final SimulatorProvider provider =
      new AutraProvider(
          merchants,
          sessions,
          payments,
          pixPayments,
          cardSessions,
          audit,
          fixtures,
          rules,
          new AutraDoubles.HeldResponses(),
          testRunId -> now.get(),
          new OpenAcquiringSession(sessions, ids, scenarios),
          new OpenCardSession(cardSessions, ids, scenarios, AutraWiring.PUBLIC_BASE_URL),
          new ChargeStoredCard(
              payments, sessions, new AutraStoredCards(cardSessions, fixtures), ids, scenarios),
          new TokenizeOnTheHostedPage(cardSessions, new AutraPayerCards(fixtures), ids),
          new PreAuthorize(
              payments, sessions, new AutraStoredCards(cardSessions, fixtures), ids, scenarios),
          new CaptureHold(payments, scenarios),
          new ReverseOrRelease(payments, scenarios),
          new CreatePixCheckout(pixPayments, sessions, ids, scenarios),
          new LookUpPayment(payments),
          new ListPayments(payments),
          new CompletePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork()),
          new SettlePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork()),
          callbacks,
          callbackDeliveries);

  private final PayerOnTheHostedCardPage page =
      new PayerOnTheHostedCardPage(
          cardSessions, provider, audit, testRunId -> now.get(), new AutraDoubles.CountingIds());

  @Test
  void the_hosted_card_url_is_the_page_this_simulator_serves_under_its_public_base_url() {
    AutraCardSession session = aSessionOf(RUN);
    URI url =
        URI.create(
            AutraCardPage.urlFor(
                AutraWiring.PUBLIC_BASE_URL, session.cardSessionId(), session.pageToken()));

    assertThat(url.getScheme() + "://" + url.getAuthority()).isEqualTo(AutraWiring.PUBLIC_BASE_URL);
    assertThat(url.getPath()).matches("/tokenize/sessions/[0-9a-f-]{36}");
    assertThat(url.getQuery()).matches(AutraCardPage.TOKEN + "=[0-9a-f]{24}");
    assertThat(url.getFragment()).isNull();
  }

  @Test
  void the_page_offers_every_card_this_run_offers_and_only_the_outcomes_the_provider_takes() {
    seedACard();
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered opened = open(session);

    assertThat(opened.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
    assertThat(opened.body())
        .contains("value=\"" + AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS + "\"")
        .contains("value=\"" + A_SEEDED_CARD + "\"")
        .contains("name=\"" + AutraCardPage.CARD + "\"")
        .contains("value=\"" + PayerOutcome.PAID.name() + "\"")
        .contains("value=\"" + PayerOutcome.ABANDONED.name() + "\"")
        .doesNotContain("value=\"" + PayerOutcome.DECLINED.name() + "\"")
        .contains("name=\"viewport\"");
  }

  @Test
  void every_answer_of_this_page_carries_the_four_defences_in_one_order() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered opened = open(session);
    PayerOnTheHostedCardPage.Rendered refused = save(session, "no-such-card");
    PayerOnTheHostedCardPage.Rendered unknown = page.open(session.cardSessionId(), "0".repeat(24));

    assertThat(List.of(opened.httpStatus(), refused.httpStatus(), unknown.httpStatus()))
        .as("three kinds of answer and not the same one three times")
        .containsExactly(
            PayerOnTheHostedCardPage.OFFERED,
            PayerOnTheHostedCardPage.REFUSED,
            PayerOnTheHostedCardPage.NO_SUCH_PAGE);
    for (PayerOnTheHostedCardPage.Rendered answer : List.of(opened, refused, unknown)) {
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
    }
  }

  @Test
  void the_page_prints_no_credential_of_the_merchant_it_belongs_to() {
    PayerOnTheHostedCardPage.Rendered opened = open(aSessionOf(RUN));

    assertThat(opened.body())
        .doesNotContain("secret-" + ACCOUNT)
        .doesNotContain("client-" + ACCOUNT)
        .doesNotContain(DOCUMENT);
  }

  @Test
  void the_page_token_reaches_no_read_of_the_session_and_no_audit_row() {
    AutraCardSession session = aSessionOf(RUN);
    save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    assertThat(String.valueOf(provider.state(RUN, session.cardSessionId()).orElseThrow()))
        .doesNotContain(tokenOf(session));
    assertThat(String.valueOf(audit.all())).doesNotContain(tokenOf(session));
  }

  @Test
  void a_payer_who_saves_a_card_leaves_the_session_the_control_route_would_have_left() {
    seedACard();
    AutraCardSession onThePage = aSessionOf(RUN);
    AutraCardSession overTheControlRoute = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered saved = save(onThePage, A_SEEDED_CARD);
    HostedCompletionOutcome controlled =
        provider.complete(
            HostedCompletionControl.asked(
                provider.hostedCompletion(RUN, overTheControlRoute.cardSessionId()),
                RUN,
                overTheControlRoute.cardSessionId(),
                PayerOutcome.PAID.name(),
                A_SEEDED_CARD,
                ObjectOrigin.CONTROL_API));

    assertThat(saved.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
    assertThat(controlled).isInstanceOf(HostedCompletionOutcome.Completed.class);
    ProviderObjectState byThePage = provider.state(RUN, onThePage.cardSessionId()).orElseThrow();
    ProviderObjectState byTheRoute =
        provider.state(RUN, overTheControlRoute.cardSessionId()).orElseThrow();
    assertThat(byThePage.nativeStatus()).isEqualTo(AutraCardSession.TOKENIZED);
    assertThat(byThePage.nativeStatus()).isEqualTo(byTheRoute.nativeStatus());
    assertThat(byThePage.normalizedStatus()).isEqualTo(byTheRoute.normalizedStatus());
    assertThat(byThePage.relatedObjects()).isEqualTo(byTheRoute.relatedObjects());
  }

  @Test
  void the_page_a_payer_saved_a_card_on_hands_back_the_result_autra_publishes_for_the_sdk() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered saved =
        save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(saved.body())
        .contains("id=\"" + AutraCardPage.RESULT + "\"")
        .contains("&quot;slugToken&quot;:&quot;" + stored.slugToken() + "&quot;")
        .contains("&quot;slugStoredCard&quot;:&quot;" + stored.slugStoredCard() + "&quot;")
        .contains("&quot;tokenExpirationDate&quot;:&quot;122030&quot;")
        .contains("&quot;brand&quot;:&quot;VISA&quot;")
        .contains("&quot;first4&quot;:&quot;4111&quot;")
        .contains("&quot;last4&quot;:&quot;1111&quot;")
        .contains("&quot;expirationMonth&quot;:12")
        .contains("&quot;expirationYear&quot;:2030");
  }

  @Test
  void a_payer_who_leaves_the_page_leaves_the_session_expired_and_no_card_behind() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered left =
        page.submit(session.cardSessionId(), tokenOf(session), PayerOutcome.ABANDONED.name(), null);

    assertThat(left.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
    assertThat(left.body()).doesNotContain(AutraCardPage.RESULT);
    AutraCardSession after = cardSessions.findById(RUN, session.cardSessionId()).orElseThrow();
    assertThat(after.nativeStatus()).isEqualTo(AutraStatus.EXPIRED);
    assertThat(after.storedCard()).isNull();
  }

  @Test
  void a_decline_the_provider_does_not_take_is_refused_in_its_own_words_and_changes_nothing() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered declined =
        page.submit(session.cardSessionId(), tokenOf(session), PayerOutcome.DECLINED.name(), null);

    assertThat(declined.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.REFUSED);
    assertThat(declined.body()).contains("configuration_error");
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void a_card_this_run_does_not_offer_is_refused_and_the_session_keeps_waiting() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered refused = save(session, "no-such-card");

    assertThat(refused.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.REFUSED);
    assertThat(refused.body()).contains(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void a_submission_naming_no_outcome_is_refused_and_the_session_keeps_waiting() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered refused =
        page.submit(session.cardSessionId(), tokenOf(session), null, null);

    assertThat(refused.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.REFUSED);
    assertThat(refused.body()).contains("choose what the payer did");
    List<AuditRecord> recorded = audit.of(AutraApi.AUTRA, RUN);
    assertThat(recorded.get(recorded.size() - 2).sanitizedBody()).isEmpty();
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void a_token_no_session_was_minted_with_opens_no_page_and_finishes_nothing() {
    AutraCardSession session = aSessionOf(RUN);

    assertThat(page.open(session.cardSessionId(), "0".repeat(24)).httpStatus())
        .isEqualTo(PayerOnTheHostedCardPage.NO_SUCH_PAGE);
    assertThat(page.open(session.cardSessionId(), null).httpStatus())
        .isEqualTo(PayerOnTheHostedCardPage.NO_SUCH_PAGE);
    assertThat(
            page.submit(
                    session.cardSessionId(),
                    "0".repeat(24),
                    PayerOutcome.PAID.name(),
                    AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS)
                .httpStatus())
        .isEqualTo(PayerOnTheHostedCardPage.NO_SUCH_PAGE);
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void the_token_of_one_session_opens_no_other_session() {
    AutraCardSession one = aSessionOf(RUN);
    AutraCardSession other = aSessionOf(RUN);

    assertThat(page.open(other.cardSessionId(), tokenOf(one)).httpStatus())
        .isEqualTo(PayerOnTheHostedCardPage.NO_SUCH_PAGE);
  }

  @Test
  void the_page_finds_the_run_of_its_session_through_the_link_alone() {
    AutraCardSession elsewhere = aSessionOf(ANOTHER_RUN);

    PayerOnTheHostedCardPage.Rendered saved =
        save(elsewhere, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    assertThat(saved.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
    assertThat(
            cardSessions
                .findById(ANOTHER_RUN, elsewhere.cardSessionId())
                .orElseThrow()
                .nativeStatus())
        .isEqualTo(AutraCardSession.TOKENIZED);
    assertThat(cardSessions.findById(RUN, elsewhere.cardSessionId())).isEmpty();
    assertThat(audit.of(AutraApi.AUTRA, ANOTHER_RUN))
        .extracting(AuditRecord::path)
        .contains("/tokenize/sessions/" + elsewhere.cardSessionId());
  }

  @Test
  void a_session_already_saved_no_longer_loads_and_refuses_a_second_card() {
    AutraCardSession session = aSessionOf(RUN);
    save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    PayerOnTheHostedCardPage.Rendered reopened = open(session);
    PayerOnTheHostedCardPage.Rendered again =
        save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    assertThat(reopened.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.GONE);
    assertThat(reopened.body()).contains("uso unico").doesNotContain("<form");
    assertThat(again.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.REFUSED);
    assertThat(again.body()).contains("uso unico");
  }

  @Test
  void a_session_is_offered_until_the_last_instant_of_its_fifteen_minutes() {
    AutraCardSession session = aSessionOf(RUN);
    now.set(NOW.plus(AutraCardSession.THE_PUBLISHED_TTL).minusMillis(1));

    assertThat(open(session).httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
  }

  @Test
  void a_session_past_its_fifteen_minutes_no_longer_loads_and_refuses_a_card() {
    AutraCardSession session = aSessionOf(RUN);
    now.set(NOW.plus(AutraCardSession.THE_PUBLISHED_TTL));

    PayerOnTheHostedCardPage.Rendered reopened = open(session);
    PayerOnTheHostedCardPage.Rendered late =
        save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    assertThat(reopened.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.GONE);
    assertThat(reopened.body()).contains("no longer loads").doesNotContain("<form");
    assertThat(late.httpStatus()).isEqualTo(PayerOnTheHostedCardPage.REFUSED);
    assertThat(late.body()).contains("no longer loads");
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard())
        .isNull();
  }

  @Test
  void a_submission_is_recorded_as_the_payer_exchange_and_the_token_is_not() {
    AutraCardSession session = aSessionOf(RUN);
    int before = audit.of(AutraApi.AUTRA, RUN).size();

    save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    List<AuditRecord> recorded = audit.of(AutraApi.AUTRA, RUN);
    List<AuditRecord> added = recorded.subList(before, recorded.size());
    assertThat(added)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    assertThat(added.get(0).sanitizedBody())
        .containsEntry(AutraCardPage.OUTCOME, List.of(PayerOutcome.PAID.name()))
        .containsEntry(AutraCardPage.CARD, List.of(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS));
    assertThat(added.get(1).httpStatus()).isEqualTo(PayerOnTheHostedCardPage.OFFERED);
    assertThat(added.get(1).providerObjectId()).isEqualTo(session.cardSessionId());
    assertThat(String.valueOf(added)).doesNotContain(tokenOf(session));
  }

  @Test
  void only_the_form_that_saves_a_card_asks_which_card_and_the_form_that_leaves_does_not() {
    PayerOnTheHostedCardPage.Rendered opened = open(aSessionOf(RUN));

    List<String> forms =
        Arrays.stream(opened.body().split("</form>")).filter(f -> f.contains("<form")).toList();
    assertThat(forms).hasSize(2);
    String saving = theFormWhoseButtonIs(forms, "PAID");
    String leaving = theFormWhoseButtonIs(forms, "ABANDONED");
    assertThat(saving)
        .contains("<select id=\"card\" name=\"card\">")
        .contains("<option value=\"published-4111\">");
    assertThat(leaving).doesNotContain("<select").doesNotContain("name=\"card\"");
  }

  @Test
  void a_payer_who_leaves_the_page_is_told_no_card_was_saved_and_to_open_a_new_session() {
    AutraCardSession session = aSessionOf(RUN);

    PayerOnTheHostedCardPage.Rendered left =
        page.submit(session.cardSessionId(), tokenOf(session), "ABANDONED", null);

    assertThat(left.body())
        .startsWith("<!DOCTYPE html>")
        .contains("<h1>No card saved</h1>")
        .contains("You left without saving a card.")
        .contains("the merchant&#39;s app opens a new one to try again.")
        .doesNotContain("<form");
  }

  @Test
  void the_result_carries_the_nickname_the_merchant_gave_the_session() {
    AutraCardSession session =
        aSessionOf(RUN, "{\"documentId\":\"" + DOCUMENT + "\",\"nickname\":\"Personal Visa\"}");

    PayerOnTheHostedCardPage.Rendered saved = save(session, "published-4111");

    assertThat(saved.body()).contains("&quot;nickname&quot;:&quot;Personal Visa&quot;");
  }

  @Test
  void the_result_of_a_session_given_no_nickname_or_a_blank_one_has_no_nickname_field() {
    AutraCardSession unnamed = aSessionOf(RUN);
    AutraCardSession blank =
        aSessionOf(RUN, "{\"documentId\":\"" + DOCUMENT + "\",\"nickname\":\"  \"}");

    PayerOnTheHostedCardPage.Rendered savedUnnamed = save(unnamed, "published-4111");
    PayerOnTheHostedCardPage.Rendered savedBlank = save(blank, "published-4111");

    assertThat(savedUnnamed.body()).contains("&quot;slugToken&quot;").doesNotContain("nickname");
    assertThat(savedBlank.body()).contains("&quot;slugToken&quot;").doesNotContain("nickname");
  }

  @Test
  void a_page_that_answers_is_recorded_as_a_success_naming_no_refused_object() {
    AutraCardSession session = aSessionOf(RUN);

    save(session, "published-4111");

    AuditRecord answered = theLastRecordOf(RUN);
    assertThat(answered.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.refusal()).isNull();
    assertThat(answered.providerObjectId()).isEqualTo(session.cardSessionId());
  }

  @Test
  void a_page_that_refuses_is_recorded_as_a_failure_naming_the_card_session_it_refused() {
    AutraCardSession session = aSessionOf(RUN);

    save(session, "no-such-card");

    AuditRecord refused = theLastRecordOf(RUN);
    assertThat(refused.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(refused.httpStatus()).isEqualTo(422);
    assertThat(refused.refusal()).isEqualTo(new RefusalSubject.TheObject(session.cardSessionId()));
    assertThat(refused.providerObjectId()).isEqualTo(session.cardSessionId());
  }

  private static String theFormWhoseButtonIs(List<String> forms, String outcome) {
    return forms.stream()
        .filter(f -> f.contains("name=\"outcome\" value=\"" + outcome + "\""))
        .findFirst()
        .orElseThrow();
  }

  private AuditRecord theLastRecordOf(TestRunId run) {
    List<AuditRecord> recorded = audit.of(AutraApi.AUTRA, run);
    return recorded.get(recorded.size() - 1);
  }

  private PayerOnTheHostedCardPage.Rendered open(AutraCardSession session) {
    return page.open(session.cardSessionId(), tokenOf(session));
  }

  private PayerOnTheHostedCardPage.Rendered save(AutraCardSession session, String card) {
    return page.submit(session.cardSessionId(), tokenOf(session), PayerOutcome.PAID.name(), card);
  }

  private static String tokenOf(AutraCardSession session) {
    return session.pageToken();
  }

  private AutraCardSession aSessionOf(TestRunId run) {
    return aSessionOf(run, "{\"documentId\":\"" + DOCUMENT + "\"}");
  }

  private AutraCardSession aSessionOf(TestRunId run, String body) {
    merchants.merge(run, List.of(AutraDoubles.merchant(run, ACCOUNT, DOCUMENT)));
    AutraApi api =
        AutraWiring.api(
            merchants,
            sessions,
            payments,
            pixPayments,
            cardSessions,
            audit,
            run,
            NOW,
            scenarios,
            new AutraDoubles.Runs(run));
    AutraApiResponse opened =
        api.cardSession(AutraWire.call(AutraApiController.CARD_SESSIONS, bearerOf(api), body));
    String sessionId =
        AutraJsonReader.objectIn(AutraWire.bodyOf(opened))
            .orElseThrow()
            .text(AutraFields.HOSTED_CARD_SESSION_ID)
            .orElseThrow();
    return cardSessions.findById(run, sessionId).orElseThrow();
  }

  private static Map<String, List<String>> bearerOf(AutraApi api) {
    return AutraWire.bearer(
        AutraTokenTest.theTokenIn(
            api.token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS,
                    AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT)))));
  }

  private void seedACard() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            AutraApi.AUTRA,
            RUN,
            ACCOUNT,
            "owner-1",
            PaymentMethodKind.CARD,
            A_SEEDED_CARD,
            new FixtureCard("mastercard", "4242", 6, 2029, "debit"),
            Map.of(
                ProviderPaymentMethodFields.TOKEN,
                "seeded-network-token",
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                "seeded-stored-card"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
  }

  @Test
  void the_row_this_page_answers_with_carries_no_field_at_all() {
    AutraCardSession session = aSessionOf(RUN);

    save(session, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    List<AuditRecord> recorded = audit.of(AutraApi.AUTRA, RUN);
    AuditRecord answered = recorded.get(recorded.size() - 1);
    assertThat(answered.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(answered.sanitizedBody())
        .as(
            "this page answers a browser with a document, so autra's declaration is applied to an"
                + " empty body here and this row is the same row it was before it was")
        .isEmpty();
  }
}
