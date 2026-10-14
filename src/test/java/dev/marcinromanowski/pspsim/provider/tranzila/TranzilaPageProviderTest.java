package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaPageProviderTest {

  private final TranzilaWiring wired = new TranzilaWiring();

  private final TranzilaProvider provider = wired.provider;

  @Test
  void a_page_waiting_for_its_payer_is_an_object_a_payer_finishes_named_by_its_contact() {
    open("A", "contact", "Dana Levi");
    TranzilaPage page = thePage();

    ProviderObjectState state = provider.state(TranzilaWiring.RUN, page.pageId()).orElseThrow();

    assertThat(state.providerObjectId()).isEqualTo(page.pageId());
    assertThat(state.kind()).isEqualTo(TranzilaObjectKinds.A_PAYMENT_PAGE);
    assertThat(state.nativeStatus()).isEqualTo("awaiting the payer");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(state.amount().value()).isEqualTo("10");
    assertThat(state.currency()).isEqualTo("ILS");
    assertThat(state.ledger().operations()).isEmpty();
    assertThat(state.capturesAccepted()).isFalse();
    assertThat(state.refundsAccepted()).isFalse();
    assertThat(state.authorizationOpen()).isFalse();
    assertThat(state.relatedObjects()).containsExactly(Map.entry("terminalName", "myterminal"));
    assertThat(state.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(state.updatedAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(state.hostedSession())
        .isEqualTo(
            new HostedSessionFacts.AnObjectAPayerFinishes(
                TranzilaWiring.ACCOUNT,
                new HostedSessionFacts.Party.NamedBy("Dana Levi"),
                "tranmode A",
                TranzilaWiring.NOW,
                new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(
                    TranzilaPageObject.NO_DEADLINE)));
  }

  @Test
  void a_paid_page_reads_as_the_transaction_it_made_and_names_it() {
    open("V");
    TranzilaPage opened = thePage();
    wired.clock.moveTo(Instant.parse("2026-10-13T12:05:00Z"));
    pay(opened.pageId());
    TranzilaTransaction j5 = wired.theLastStored();

    ProviderObjectState state = provider.state(TranzilaWiring.RUN, opened.pageId()).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("verify 5 777");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(state.updatedAt()).isEqualTo(Instant.parse("2026-10-13T12:05:00Z"));
    assertThat(state.relatedObjects())
        .containsExactly(
            Map.entry("terminalName", "myterminal"),
            Map.entry("transaction_id", j5.transactionIdAsText()));
    assertThat(state.hostedSession())
        .isInstanceOfSatisfying(
            HostedSessionFacts.AnObjectAPayerFinishes.class,
            facts -> assertThat(facts.thePartyAsItReads()).isEqualTo(TranzilaPageObject.NO_PARTY));
  }

  @Test
  void a_page_left_or_saved_as_a_token_reads_in_its_own_words() {
    open("A");
    String left = thePage().pageId();
    submit(left, Map.of("outcome", List.of("ABANDONED")));
    open("K");
    String saved = thePage().pageId();
    pay(saved);

    ProviderObjectState leftState = provider.state(TranzilaWiring.RUN, left).orElseThrow();
    ProviderObjectState savedState = provider.state(TranzilaWiring.RUN, saved).orElseThrow();

    assertThat(leftState.nativeStatus()).isEqualTo("left without paying");
    assertThat(leftState.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(savedState.nativeStatus()).isEqualTo("K 777");
    assertThat(savedState.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_page_whose_transaction_is_no_longer_held_reads_by_its_own_outcome() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    open("A");
    String declined = thePage().pageId();
    pay(declined);
    open("V");
    String paid = thePage().pageId();
    pay(paid);
    wired.transactions.clear(TranzilaWiring.RUN);

    ProviderObjectState declinedState = provider.state(TranzilaWiring.RUN, declined).orElseThrow();
    ProviderObjectState paidState = provider.state(TranzilaWiring.RUN, paid).orElseThrow();

    assertThat(declinedState.nativeStatus()).isEqualTo("declined");
    assertThat(declinedState.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(paidState.nativeStatus()).isEqualTo("paid");
    assertThat(paidState.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void pages_and_transactions_are_listed_together_newest_first_and_read_together_by_id() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    open("A");
    String first = thePage().pageId();
    pay(first);
    long paid = wired.theLastStored().transactionId();
    open("A");
    String second = thePage().pageId();

    ProviderObjectPage.Listed all =
        (ProviderObjectPage.Listed)
            provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, null, 10));
    ProviderObjectPage.Listed afterThePage =
        (ProviderObjectPage.Listed)
            provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, first, 10));

    assertThat(all.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(second, Long.toString(paid), first, Long.toString(debit));
    assertThat(afterThePage.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(Long.toString(debit));
    assertThat(
            provider.theObjectsItHoldsUnder(
                TranzilaWiring.RUN, List.of(first, Long.toString(debit), "directng_nothing")))
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactlyInAnyOrder(first, Long.toString(debit));
    assertThat(provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, "directng_x", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void a_reset_forgets_the_pages_and_the_handshakes_of_the_run() {
    open("A");
    wired.handshakes.insert(
        new TranzilaHandshake(
            "w" + "a".repeat(41),
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "1",
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plusSeconds(1)));

    int forgotten = provider.reset(TranzilaWiring.RUN, ResetScope.TEST_RUN).clearedObjects();
    open("A");
    wired.handshakes.insert(
        new TranzilaHandshake(
            "w" + "b".repeat(41),
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "1",
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plusSeconds(1)));
    int forgottenEverywhere = provider.reset(TranzilaWiring.RUN, ResetScope.ALL).clearedObjects();

    assertThat(forgotten).isEqualTo(2);
    assertThat(forgottenEverywhere).isEqualTo(2);
    assertThat(wired.pages.all()).isEmpty();
    assertThat(wired.handshakes.all()).isEmpty();
  }

  @Test
  void a_screen_completes_a_page_through_the_provider_and_nothing_else_through_it() {
    open("A");
    TranzilaPage page = thePage();

    HostedCompletionOffer offer = provider.hostedCompletion(TranzilaWiring.RUN, page.pageId());
    HostedCompletionOutcome completed =
        provider.complete(
            new HostedCompletionRequest(
                TranzilaWiring.RUN,
                page.pageId(),
                PayerOutcome.PAID,
                "published-9022",
                ObjectOrigin.UI));

    assertThat(offer).isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(completed).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(provider.hostedCompletion(TranzilaWiring.RUN, "directng_nothing"))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                TranzilaApi.TRANZILA, TranzilaProvider.NO_PAGE_TO_COMPLETE));
  }

  @Test
  void a_screen_starts_a_page_payment_that_waits_for_its_payer_or_is_refused_its_parameters() {
    OriginationOutcome started = provider.originate(aPagePayment("10.53", "978", "VK"));
    OriginationOutcome refused = provider.originate(aPagePayment("10.53", "978", "J"));
    OriginationOutcome noAccount =
        provider.originate(
            OriginationRequest.of(
                TranzilaWiring.RUN,
                "nobody",
                TranzilaOrigination.A_PAGE_PAYMENT,
                Map.of("sum", "1", "currency", "1", "tranmode", "A"),
                ObjectOrigin.UI));

    TranzilaPage page = thePage();
    assertThat(started)
        .isEqualTo(
            new OriginationOutcome.Originated(
                page.pageId(),
                TranzilaOrigination.DECLARED
                    .wayNamed(TranzilaOrigination.A_PAGE_PAYMENT)
                    .orElseThrow()
                    .whatItLeavesBehind()));
    assertThat(page.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(page.sum()).isEqualTo("10.53");
    assertThat(page.currency()).isEqualTo(TranzilaPageCurrency.EURO);
    assertThat(page.tranmode()).isEqualTo(TranzilaTranmode.VK);
    assertThat(page.successUrl()).isNull();
    assertThat(refused)
        .isEqualTo(
            new OriginationOutcome.RefusedByTheProvider(
                TranzilaPageRequest.A_STANDING_ORDER_IS_NOT_SERVED));
    assertThat(noAccount).isEqualTo(new OriginationOutcome.NoSuchAccount());
    assertThat(wired.pages.all()).hasSize(1);
  }

  @Test
  void the_page_payment_is_declared_as_a_page_this_simulator_serves_at_its_route() {
    var declared =
        TranzilaOrigination.DECLARED.wayNamed(TranzilaOrigination.A_PAGE_PAYMENT).orElseThrow();

    assertThat(declared.thePayerStep())
        .isEqualTo(
            new ThePayerStep.APageThisSimulatorServes(
                "/tranzila/directng/{terminal}/iframenew.php"));
    assertThat(declared.keys()).containsExactly("sum", "currency", "tranmode");
    assertThat(
            TranzilaOrigination.theParametersOf(
                OriginationRequest.of(
                    TranzilaWiring.RUN,
                    TranzilaWiring.ACCOUNT,
                    TranzilaOrigination.A_PAGE_PAYMENT,
                    Map.of("sum", "1", "tranmode", "A"),
                    ObjectOrigin.UI)))
        .containsExactly(Map.entry("sum", List.of("1")), Map.entry("tranmode", List.of("A")));
  }

  private OriginationRequest aPagePayment(String sum, String currency, String tranmode) {
    return OriginationRequest.of(
        TranzilaWiring.RUN,
        TranzilaWiring.ACCOUNT,
        TranzilaOrigination.A_PAGE_PAYMENT,
        Map.of("sum", sum, "currency", currency, "tranmode", tranmode),
        ObjectOrigin.UI);
  }

  private TranzilaPage thePage() {
    List<TranzilaPage> pages = wired.pages.all();
    return pages.get(pages.size() - 1);
  }

  private void open(String tranmode, String... extra) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("sum", List.of("10"));
    parameters.put("currency", List.of("1"));
    parameters.put("tranmode", List.of(tranmode));
    for (int at = 0; at < extra.length; at += 2) {
      parameters.put(extra[at], List.of(extra[at + 1]));
    }
    wired.directNg.open(
        TranzilaPageCall.of("POST", "/tranzila/directng/myterminal/iframenew.php", parameters),
        TranzilaWiring.TERMINAL);
  }

  private void pay(String pageId) {
    submit(pageId, Map.of("outcome", List.of("PAID"), "card", List.of("published-9022")));
  }

  private void submit(String pageId, Map<String, List<String>> form) {
    wired.directNg.submit(TranzilaPageCall.of("POST", "/x", form), TranzilaWiring.TERMINAL, pageId);
  }
}
