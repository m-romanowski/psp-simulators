package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveryRequest;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class TranzilaNotify {

  static final String NO_EVENT_NAME =
      "provider tranzila names no notify event: the payment page posts the transaction data to"
          + " notify_url_address and nothing else, so eventType names nothing this page could send"
          + " instead";

  static final String NO_SUCH_OBJECT =
      "no tranzila payment page and no transaction with that providerObjectId in this test run";

  static final String NOT_A_PAGE_ATTEMPT =
      "this transaction was not made on the payment page, and a notify is sent again only for a"
          + " page: the card API answers every transaction synchronously and notifies nobody, and"
          + " a hosted fields charge's notify is sent once when it is made and retried, and is not"
          + " kept to be sent again";

  static final String NO_ATTEMPT_FINISHED =
      "this payment page has no finished attempt to report: a notify is sent for an approved or a"
          + " declined attempt, and this page is still waiting for its payer or was left without"
          + " paying";

  static final String NOT_A_TARGET_OF_THE_ACCOUNT =
      "notify_url_address is not among the callback targets configured for this terminal's"
          + " account in this test run, and a simulator that posted to it would deliver to an"
          + " address no test declared for that account";

  static final String NO_SINGLE_TARGET =
      "the page carried no notify_url_address and the terminal's account has no single configured"
          + " callback target to stand for the notify page of the terminal's settings, so there is"
          + " no address a notify would be posted to";

  private final ProviderAccounts accounts;
  private final WebhookDispatcher dispatcher;
  private final TranzilaPages pages;
  private final TranzilaTransactions transactions;

  TranzilaNotify(
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      TranzilaPages pages,
      TranzilaTransactions transactions) {
    this.accounts = accounts;
    this.dispatcher = dispatcher;
    this.pages = pages;
    this.transactions = transactions;
  }

  sealed interface Target {

    record Addressed(String url) implements Target {}

    record NoneConfigured(String inItsOwnWords) implements Target {}

    record Unaddressable(String inItsOwnWords) implements Target {}
  }

  Target targetOf(TestRunId testRunId, String accountId, @Nullable String requested) {
    List<WebhookTarget> ofTheAccount = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(TranzilaApi.TRANZILA, testRunId)) {
      if (target.accountId().equals(accountId)) {
        ofTheAccount.add(target);
      }
    }
    if (requested != null) {
      for (WebhookTarget target : ofTheAccount) {
        if (target.url().equals(requested)) {
          return new Target.Addressed(target.url());
        }
      }
      return new Target.Unaddressable(NOT_A_TARGET_OF_THE_ACCOUNT);
    }
    if (ofTheAccount.size() != 1) {
      return new Target.NoneConfigured(NO_SINGLE_TARGET);
    }
    return new Target.Addressed(ofTheAccount.get(0).url());
  }

  List<ScheduledWebhook> deliverOnce(
      TranzilaPage page, @Nullable TranzilaTransaction transaction, Target target) {
    if (target instanceof Target.Addressed addressed) {
      return deliver(page, transaction, addressed.url(), null, null);
    }
    return List.of();
  }

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    if (request.eventType() != null) {
      throw new WebhookNotSchedulableException(NO_EVENT_NAME);
    }
    TestRunId testRunId = request.testRunId();
    TranzilaPage page = thePageNamed(testRunId, request.providerObjectId());
    PayerOutcome outcome = page.outcome();
    if (outcome == null || outcome == PayerOutcome.ABANDONED) {
      throw new WebhookNotSchedulableException(NO_ATTEMPT_FINISHED);
    }
    TranzilaTransaction transaction = theTransactionOf(page).orElse(null);
    return switch (targetOf(testRunId, page.accountId(), page.notifyUrl())) {
      case Target.Addressed addressed ->
          deliver(page, transaction, addressed.url(), request.reuseEventId(), request.delay());
      case Target.NoneConfigured none ->
          throw new WebhookNotSchedulableException(none.inItsOwnWords());
      case Target.Unaddressable unaddressable ->
          throw new WebhookNotSchedulableException(unaddressable.inItsOwnWords());
    };
  }

  private TranzilaPage thePageNamed(TestRunId testRunId, String providerObjectId) {
    Optional<TranzilaPage> page = pages.find(testRunId, providerObjectId);
    if (page.isPresent()) {
      return page.get();
    }
    Optional<Long> transactionId = TranzilaIdentifiers.transactionIdIn(providerObjectId);
    if (transactionId.isEmpty()
        || transactions.paymentHolding(testRunId, transactionId.get().longValue()).isEmpty()) {
      throw new UnknownProviderObjectException(NO_SUCH_OBJECT);
    }
    return pages
        .ofTheTransaction(testRunId, transactionId.get().longValue())
        .orElseThrow(() -> new WebhookNotSchedulableException(NOT_A_PAGE_ATTEMPT));
  }

  Optional<TranzilaTransaction> theTransactionOf(TranzilaPage page) {
    Long transactionId = page.transactionId();
    if (transactionId == null) {
      return Optional.empty();
    }
    long made = transactionId.longValue();
    return transactions
        .paymentHolding(page.testRunId(), made)
        .flatMap(payment -> payment.transaction(made));
  }

  void deliverOnce(
      TestRunId testRunId,
      String accountId,
      String objectId,
      Map<String, String> fields,
      Target target) {
    if (target instanceof Target.Addressed addressed) {
      deliver(testRunId, accountId, objectId, fields, addressed.url(), null, null);
    }
  }

  private List<ScheduledWebhook> deliver(
      TranzilaPage page,
      @Nullable TranzilaTransaction transaction,
      String url,
      @Nullable String reuseEventId,
      @Nullable Duration delay) {
    return deliver(
        page.testRunId(),
        page.accountId(),
        transaction == null ? page.pageId() : transaction.transactionIdAsText(),
        TranzilaBrowserReturn.theNotifyOf(page, transaction),
        url,
        reuseEventId,
        delay);
  }

  private List<ScheduledWebhook> deliver(
      TestRunId testRunId,
      String accountId,
      String objectId,
      Map<String, String> fields,
      String url,
      @Nullable String reuseEventId,
      @Nullable Duration delay) {
    List<WebhookDelivery> scheduled =
        dispatcher.schedule(
            new WebhookDeliveryRequest(
                TranzilaApi.TRANZILA,
                testRunId,
                accountId,
                null,
                objectId,
                url,
                payloadOf(fields),
                reuseEventId,
                null,
                delay));
    List<ScheduledWebhook> jobs = new ArrayList<>();
    for (WebhookDelivery delivery : scheduled) {
      jobs.add(new ScheduledWebhook(delivery.deliveryId(), delivery.eventId()));
    }
    return List.copyOf(jobs);
  }

  static WebhookPayload payloadOf(TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    return payloadOf(TranzilaBrowserReturn.theNotifyOf(page, transaction));
  }

  static WebhookPayload payloadOf(Map<String, String> fields) {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    fields.forEach((name, value) -> sent.put(name, List.of(value)));
    return new WebhookPayload(
        TranzilaFields.FORM,
        Map.of(),
        TranzilaStoredForm.encoded(fields).getBytes(StandardCharsets.UTF_8),
        Redaction.redact(sent, TranzilaFields.SENSITIVE_DELIVERY_FIELDS));
  }
}
