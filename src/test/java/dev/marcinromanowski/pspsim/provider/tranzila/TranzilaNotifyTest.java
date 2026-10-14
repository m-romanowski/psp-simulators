package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TranzilaNotifyTest {

  private static final String NOTIFY = "https://shop.example/notify";

  private static final String ANOTHER = "https://shop.example/other";

  private static final String AGAIN = "evt_again";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_requested_notify_address_must_be_a_target_of_the_terminals_own_account() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    wired.accounts.callingBack(TranzilaWiring.RUN, "account-2", ANOTHER);
    wired.accounts.callingBack(TestRunId.of("elsewhere"), TranzilaWiring.ACCOUNT, ANOTHER);

    assertThat(wired.notify.targetOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY))
        .isEqualTo(new TranzilaNotify.Target.Addressed(NOTIFY));
    assertThat(wired.notify.targetOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, ANOTHER))
        .isEqualTo(
            new TranzilaNotify.Target.Unaddressable(TranzilaNotify.NOT_A_TARGET_OF_THE_ACCOUNT));
  }

  @Test
  void with_no_address_requested_the_accounts_one_target_stands_for_the_terminals_notify_page() {
    assertThat(wired.notify.targetOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, null))
        .isEqualTo(new TranzilaNotify.Target.NoneConfigured(TranzilaNotify.NO_SINGLE_TARGET));
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    assertThat(wired.notify.targetOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, null))
        .isEqualTo(new TranzilaNotify.Target.Addressed(NOTIFY));
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, ANOTHER);
    assertThat(wired.notify.targetOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, null))
        .isEqualTo(new TranzilaNotify.Target.NoneConfigured(TranzilaNotify.NO_SINGLE_TARGET));
  }

  @Test
  void a_page_with_no_target_sends_no_notify_and_its_payer_is_still_returned() {
    open("A");

    pay(thePage().pageId());

    assertThat(wired.posted).isEmpty();
    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN)).isEmpty();
  }

  @Test
  void a_notify_is_sent_again_on_request_by_the_page_or_by_the_transaction_it_made() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open("A");
    pay(thePage().pageId());
    String transaction = wired.theLastStored().transactionIdAsText();

    List<ScheduledWebhook> byPage = schedule(thePage().pageId(), null, AGAIN, null);
    List<ScheduledWebhook> byTransaction = schedule(transaction, null, null, Duration.ofMinutes(5));

    assertThat(byPage).hasSize(1);
    assertThat(byPage.get(0).eventId()).isEqualTo(AGAIN);
    assertThat(byTransaction).hasSize(1);
    assertThat(wired.posted).hasSize(2);
    assertThat(wired.posted.get(1)).isEqualTo(wired.posted.get(0));
    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN))
        .extracting(WebhookDelivery::providerObjectId)
        .containsOnly(transaction);
    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN))
        .extracting(WebhookDelivery::eventType)
        .containsOnlyNulls();
    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN))
        .extracting(WebhookDelivery::nextAttemptAt)
        .contains(TranzilaWiring.NOW.plus(Duration.ofMinutes(5)));
  }

  @Test
  void a_token_only_page_is_notified_under_its_own_id() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open("K");
    pay(thePage().pageId());

    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN))
        .singleElement()
        .extracting(WebhookDelivery::providerObjectId)
        .isEqualTo(thePage().pageId());
    assertThat(schedule(thePage().pageId(), null, null, null)).hasSize(1);
  }

  @Test
  void a_notify_names_no_event_and_is_sent_only_for_a_finished_attempt_of_a_page() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open("A");
    String waiting = thePage().pageId();
    open("A");
    String left = thePage().pageId();
    submit(left, Map.of("outcome", List.of("ABANDONED")));
    long byTheApi = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    assertThatThrownBy(() -> schedule(waiting, "notify", null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NO_EVENT_NAME);
    assertThatThrownBy(() -> schedule(waiting, null, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NO_ATTEMPT_FINISHED);
    assertThatThrownBy(() -> schedule(left, null, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NO_ATTEMPT_FINISHED);
    assertThatThrownBy(() -> schedule(Long.toString(byTheApi), null, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NOT_A_PAGE_ATTEMPT);
    assertThatThrownBy(() -> schedule("99999999", null, null, null))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(TranzilaNotify.NO_SUCH_OBJECT);
    assertThatThrownBy(() -> schedule("directng_nothing", null, null, null))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(TranzilaNotify.NO_SUCH_OBJECT);
  }

  @Test
  void a_notify_with_nowhere_to_go_is_not_scheduled_in_the_targets_own_words() {
    open("A");
    pay(thePage().pageId());
    String unaddressed = thePage().pageId();

    assertThatThrownBy(() -> schedule(unaddressed, null, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NO_SINGLE_TARGET);
    TranzilaPage asked =
        new TranzilaPage(
            "directng_" + "e".repeat(24),
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "1",
            TranzilaPageCurrency.NIS,
            TranzilaTranmode.K,
            null,
            null,
            ANOTHER,
            null,
            null,
            null,
            new LinkedHashMap<>(),
            ObjectOrigin.PROVIDER_API,
            TranzilaWiring.NOW,
            dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome.PAID,
            null,
            "777",
            "tok12345678",
            "xxxxxxxxxxxx9022",
            null,
            TranzilaWiring.NOW);
    wired.pages.open(asked);
    assertThatThrownBy(() -> schedule(asked.pageId(), null, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(TranzilaNotify.NOT_A_TARGET_OF_THE_ACCOUNT);
    assertThat(TranzilaBrowserReturn.theCardOf(asked, null)).isEmpty();
  }

  @Test
  void a_notify_the_shop_does_not_answer_200_ok_is_retried() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    wired.merchantAnswers.add(new MerchantResponse.Answered(200, "received"));
    open("A");

    pay(thePage().pageId());

    assertThat(wired.posted).hasSize(1);
    assertThat(wired.deliveries.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN))
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery.attemptsMade()).isEqualTo(1);
              assertThat(delivery.nextAttemptAt()).isEqualTo(TranzilaWiring.NOW.plusSeconds(60));
            });
  }

  @Test
  void only_a_200_whose_body_reads_ok_acknowledges_a_notify() {
    TranzilaNotifyAcknowledgement policy = new TranzilaNotifyAcknowledgement();
    WebhookDelivery any = null;

    assertThat(policy.provider()).isEqualTo(TranzilaApi.TRANZILA);
    assertThat(policy.judge(any, new MerchantResponse.Answered(200, "OK")))
        .isEqualTo(Acknowledgement.ACKNOWLEDGED);
    assertThat(policy.judge(any, new MerchantResponse.Answered(200, " OK\n")))
        .isEqualTo(Acknowledgement.ACKNOWLEDGED);
    assertThat(policy.judge(any, new MerchantResponse.Answered(200, "ok")))
        .isEqualTo(Acknowledgement.REFUSED);
    assertThat(policy.judge(any, new MerchantResponse.Answered(200, "")))
        .isEqualTo(Acknowledgement.REFUSED);
    assertThat(policy.judge(any, new MerchantResponse.Answered(201, "OK")))
        .isEqualTo(Acknowledgement.REFUSED);
    assertThat(policy.judge(any, new MerchantResponse.Answered(500, "OK")))
        .isEqualTo(Acknowledgement.REFUSED);
    assertThat(
            policy.judge(any, new MerchantResponse.NotAnswered(TransportFailure.CONNECT_TIMEOUT)))
        .isEqualTo(Acknowledgement.REFUSED);
  }

  private List<ScheduledWebhook> schedule(
      String providerObjectId,
      @Nullable String eventType,
      @Nullable String reuseEventId,
      @Nullable Duration delay) {
    return wired.provider.schedule(
        new WebhookScheduleRequest(
            TranzilaWiring.RUN,
            providerObjectId,
            eventType,
            reuseEventId,
            delay,
            ObjectOrigin.CONTROL_API));
  }

  private TranzilaPage thePage() {
    List<TranzilaPage> pages = wired.pages.all();
    return pages.get(pages.size() - 1);
  }

  private void open(String tranmode) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("sum", List.of("10"));
    parameters.put("currency", List.of("1"));
    parameters.put("tranmode", List.of(tranmode));
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
