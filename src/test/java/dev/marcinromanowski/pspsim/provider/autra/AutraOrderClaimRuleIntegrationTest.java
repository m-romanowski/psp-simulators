package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AutraOrderClaimRuleIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-order-claim-rule-it");

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  @Autowired private JdbcClient jdbc;

  @Autowired private PlatformTransactionManager transactionManager;

  private JdbcAutraPayments payments;

  @BeforeEach
  @AfterEach
  void forgetTheRun() {
    payments = new JdbcAutraPayments(jdbc, new TransactionTemplate(transactionManager));
    jdbc.sql("delete from autra_payment where test_run_id = ?").param(RUN.value()).update();
  }

  @Test
  void the_store_finds_the_two_statuses_the_sale_route_publishes_and_no_other_shape() {
    Map<String, Boolean> refusedUnder = new LinkedHashMap<>();
    refusedUnder.put(
        stored("a-charged-sale", AutraTimeline.SALE, "ACCEPTED", "500.00", null), true);
    refusedUnder.put(
        stored("a-zero-dollar-validation", AutraTimeline.SALE, "ACCEPTED", "0.00", null), true);
    refusedUnder.put(
        stored("a-captured-hold", AutraTimeline.PREAUTH, "CAPTURED", "500.00", null), true);
    refusedUnder.put(
        stored("an-open-hold", AutraTimeline.PREAUTH, "ACCEPTED", "500.00", null), true);
    refusedUnder.put(
        stored("a-declined-sale", AutraTimeline.SALE, "DECLINED", "500.00", null), false);
    refusedUnder.put(
        stored("a-declined-hold", AutraTimeline.PREAUTH, "DECLINED", "500.00", null), false);
    refusedUnder.put(
        stored("a-reversed-sale", AutraTimeline.SALE, "VOIDED", "500.00", null), false);
    refusedUnder.put(
        stored("a-partly-reversed-sale", AutraTimeline.SALE, "PARTIALLY_VOIDED", "500.00", null),
        false);
    refusedUnder.put(
        stored("a-released-hold", AutraTimeline.PREAUTH, "VOIDED", "500.00", NOW), false);

    for (Map.Entry<String, Boolean> shape : refusedUnder.entrySet()) {
      assertThat(payments.theApprovedPaymentUnder(RUN, DOCUMENT, shape.getKey()).isPresent())
          .as(shape.getKey())
          .isEqualTo(shape.getValue().booleanValue());
    }
  }

  @Test
  void an_open_reservation_blocks_a_sale_under_its_order_because_it_answers_one_of_the_two() {
    String orderId = stored("an-open-hold", AutraTimeline.PREAUTH, "ACCEPTED", "500.00", null);

    Optional<String> found = payments.theApprovedPaymentUnder(RUN, DOCUMENT, orderId);

    assertThat(found)
        .as(
            "the sale route publishes \"Se um pagamento com o mesmo orderId ja estiver ACCEPTED ou"
                + " CAPTURED, retorna 409 sem criar um novo debito\", and a reservation answers"
                + " ACCEPTED, so narrowing this query by payment_type would let a second debit"
                + " through under an order a live reservation holds")
        .isPresent();
  }

  @Test
  void the_store_and_the_provider_answer_the_published_rule_with_one_voice_over_every_shape() {
    stored("a-charged-sale", AutraTimeline.SALE, "ACCEPTED", "500.00", null);
    stored("a-zero-dollar-validation", AutraTimeline.SALE, "ACCEPTED", "0.00", null);
    stored("a-captured-hold", AutraTimeline.PREAUTH, "CAPTURED", "500.00", null);
    stored("an-open-hold", AutraTimeline.PREAUTH, "ACCEPTED", "500.00", null);
    stored("a-declined-sale", AutraTimeline.SALE, "DECLINED", "500.00", null);
    stored("a-declined-hold", AutraTimeline.PREAUTH, "DECLINED", "500.00", null);
    stored("a-reversed-sale", AutraTimeline.SALE, "VOIDED", "500.00", null);
    stored("a-partly-reversed-sale", AutraTimeline.SALE, "PARTIALLY_VOIDED", "500.00", null);
    stored("a-released-hold", AutraTimeline.PREAUTH, "VOIDED", "500.00", NOW);

    for (AutraPayment payment : payments.ofMerchant(RUN, DOCUMENT)) {
      String orderId = payment.orderId();
      assertThat(payments.theApprovedPaymentUnder(RUN, DOCUMENT, orderId).isPresent())
          .as(
              "the SQL of this store is one expression of the published rule and"
                  + " AutraPayment.isAtAStatusTheSaleRouteRefusesAnotherOrderUnder is the other;"
                  + " they are checked against each other here because nothing else makes them"
                  + " fail together when one is narrowed: "
                  + orderId)
          .isEqualTo(payment.isAtAStatusTheSaleRouteRefusesAnotherOrderUnder());
    }
  }

  private String stored(
      String orderId,
      String type,
      String nativeStatus,
      String amount,
      @Nullable Instant releasedAt) {
    assertThat(payments.insertIfAbsent(aPayment(orderId, type, nativeStatus, amount, releasedAt)))
        .as(orderId)
        .isTrue();
    return orderId;
  }

  private static AutraPayment aPayment(
      String orderId,
      String type,
      String nativeStatus,
      String amount,
      @Nullable Instant releasedAt) {
    return new AutraPayment(
        RUN,
        orderId + "-00000000-0000-4000-8000-000000000000".substring(orderId.length()),
        ACCOUNT,
        DOCUMENT,
        orderId,
        type,
        AutraFields.CREDIT,
        Amount.of(amount),
        "BRL",
        1,
        nativeStatus,
        "A1B2C3",
        "112676199769",
        "900123456789",
        null,
        null,
        theLedgerOf(type, nativeStatus, amount),
        releasedAt,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static Ledger theLedgerOf(String type, String nativeStatus, String amount) {
    if (AutraStatus.DECLINED.equals(nativeStatus)) {
      return Ledger.empty();
    }
    if (AutraTimeline.SALE.equals(type)) {
      return Ledger.empty().with(anOperation(1, OperationKind.CAPTURE, amount));
    }
    Ledger reserved = Ledger.empty().with(anOperation(1, OperationKind.AUTHORIZATION, amount));
    return AutraStatus.ACCEPTED.equals(nativeStatus) || AutraStatus.VOIDED.equals(nativeStatus)
        ? reserved
        : reserved.with(anOperation(2, OperationKind.CAPTURE, amount));
  }

  private static MonetaryOperation anOperation(int sequence, OperationKind kind, String amount) {
    return new MonetaryOperation(
        sequence,
        kind,
        Amount.of(amount),
        kind == OperationKind.CAPTURE ? Boolean.TRUE : Boolean.FALSE,
        null,
        AutraStatus.ACCEPTED,
        NormalizedStatus.SUCCEEDED,
        NOW);
  }
}
