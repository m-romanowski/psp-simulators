package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

class AutraPixMoveIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-pix-move-it");
  private static final Instant PAID_AT = Instant.parse("2026-09-22T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-22T10:01:00Z");
  private static final Instant LATER = Instant.parse("2026-09-22T10:02:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String PIX_ID = "cdb445694be7486e830f720658f9a1a1";
  private static final String FIRST_PIX_ID = "0db445694be7486e830f720658f9a1a1";
  private static final String TARGET = "https://merchant.example/autra/acquiring";

  @Autowired private JdbcClient jdbc;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private WebhookDeliveries deliveries;
  @Autowired private AfterCommit afterCommit;

  private JdbcAutraPixPayments pixPayments;

  @BeforeEach
  @AfterEach
  void forgetTheRun() {
    pixPayments = new JdbcAutraPixPayments(jdbc);
    jdbc.sql(
            """
            delete from simulator_webhook_delivery_attempt where delivery_id in
                (select delivery_id from simulator_webhook_delivery where test_run_id = ?)
            """)
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_webhook_delivery where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from autra_pix_payment where test_run_id = ?").param(RUN.value()).update();
  }

  @Test
  void two_settles_of_one_pix_the_second_after_the_first_committed_move_it_once() {
    AutraPixPayment authorized = stored(PIX_ID, AutraStatus.AUTHORIZED);

    boolean first = pixPayments.moveFromAuthorizedToSettled(authorized, NOW);
    boolean second = pixPayments.moveFromAuthorizedToSettled(authorized, LATER);

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    AutraPixPayment settled = pixPayments.findById(RUN, PIX_ID).orElseThrow();
    assertThat(settled.nativeStatus()).isEqualTo(AutraStatus.SETTLED);
    assertThat(settled.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void a_settle_of_a_pix_that_is_not_authorized_changes_nothing() {
    AutraPixPayment pending = stored(PIX_ID, AutraStatus.PENDING);
    AutraPixPayment expired = stored(FIRST_PIX_ID, AutraStatus.EXPIRED);

    assertThat(pixPayments.moveFromAuthorizedToSettled(pending, NOW)).isFalse();
    assertThat(pixPayments.moveFromAuthorizedToSettled(expired, NOW)).isFalse();

    assertUntouched(PIX_ID, AutraStatus.PENDING);
    assertUntouched(FIRST_PIX_ID, AutraStatus.EXPIRED);
  }

  @Test
  void two_payer_steps_on_one_pix_the_second_after_the_first_committed_move_it_once() {
    AutraPixPayment pending = stored(PIX_ID, AutraStatus.PENDING);

    boolean paid = pixPayments.moveFromPendingTo(pending, AutraStatus.AUTHORIZED, NOW);
    boolean abandoned = pixPayments.moveFromPendingTo(pending, AutraStatus.EXPIRED, LATER);

    assertThat(paid).isTrue();
    assertThat(abandoned).isFalse();
    AutraPixPayment authorized = pixPayments.findById(RUN, PIX_ID).orElseThrow();
    assertThat(authorized.nativeStatus()).isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(authorized.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void a_payer_step_on_a_pix_that_is_not_pending_changes_nothing() {
    AutraPixPayment settled = stored(PIX_ID, AutraStatus.SETTLED);

    assertThat(pixPayments.moveFromPendingTo(settled, AutraStatus.EXPIRED, NOW)).isFalse();

    assertUntouched(PIX_ID, AutraStatus.SETTLED);
  }

  private void assertUntouched(String pixId, String nativeStatus) {
    AutraPixPayment read = pixPayments.findById(RUN, pixId).orElseThrow();
    assertThat(read.nativeStatus()).isEqualTo(nativeStatus);
    assertThat(read.updatedAt()).isEqualTo(PAID_AT);
  }

  @Test
  void a_settlement_whose_payout_cannot_be_queued_leaves_the_pix_authorized_and_queues_nothing() {
    SettlePixPayment settlement =
        new SettlePixPayment(pixPayments, callbacksMintingOneJobIdOnly(), unitOfWork);
    stored(FIRST_PIX_ID, AutraStatus.AUTHORIZED);
    stored(PIX_ID, AutraStatus.AUTHORIZED);
    assertThat(settlement.perform(settle(FIRST_PIX_ID), NOW)).hasSize(1);

    assertThatThrownBy(() -> settlement.perform(settle(PIX_ID), NOW))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(pixPayments.findById(RUN, FIRST_PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.SETTLED);
    assertThat(queued())
        .extracting(WebhookDelivery::providerObjectId)
        .containsExactly(FIRST_PIX_ID);
  }

  @Test
  void a_payer_step_whose_callback_cannot_be_queued_leaves_the_pix_pending_and_queues_nothing() {
    CompletePixPayment payerStep =
        new CompletePixPayment(pixPayments, callbacksMintingOneJobIdOnly(), unitOfWork);
    stored(FIRST_PIX_ID, AutraStatus.PENDING);
    stored(PIX_ID, AutraStatus.PENDING);
    assertThat(payerStep.complete(paid(FIRST_PIX_ID), NOW))
        .isInstanceOf(HostedCompletionOutcome.Completed.class);

    assertThatThrownBy(() -> payerStep.complete(paid(PIX_ID), NOW))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
    assertThat(pixPayments.findById(RUN, FIRST_PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(queued())
        .extracting(WebhookDelivery::providerObjectId)
        .containsExactly(FIRST_PIX_ID);
  }

  @Test
  void a_settle_whose_pix_a_reset_removes_before_the_move_is_answered_as_an_unknown_object() {
    stored(PIX_ID, AutraStatus.AUTHORIZED);
    SettlePixPayment settlement =
        new SettlePixPayment(
            new RemovedByAnotherRequestBeforeTheMove(pixPayments, this::removedOnItsOwnConnection),
            callbacksMintingOneJobIdOnly(),
            unitOfWork);

    assertThatThrownBy(() -> settlement.perform(settle(PIX_ID), NOW))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(SettlePixPayment.THE_RUN_HOLDS_NO_SUCH_PIX);

    assertThat(pixPayments.findById(RUN, PIX_ID)).isEmpty();
    assertThat(queued()).isEmpty();
  }

  @Test
  void a_payer_step_whose_pix_a_reset_removes_before_the_move_is_answered_as_no_such_object() {
    stored(PIX_ID, AutraStatus.PENDING);
    CompletePixPayment payerStep =
        new CompletePixPayment(
            new RemovedByAnotherRequestBeforeTheMove(pixPayments, this::removedOnItsOwnConnection),
            callbacksMintingOneJobIdOnly(),
            unitOfWork);

    assertThat(payerStep.complete(paid(PIX_ID), NOW))
        .isEqualTo(new HostedCompletionOutcome.NoSuchObject());

    assertThat(pixPayments.findById(RUN, PIX_ID)).isEmpty();
    assertThat(queued()).isEmpty();
  }

  private void removedOnItsOwnConnection() {
    CompletableFuture.runAsync(
            () ->
                jdbc.sql("delete from autra_pix_payment where test_run_id = ? and pix_id = ?")
                    .params(RUN.value(), PIX_ID)
                    .update())
        .join();
  }

  @Test
  void a_settlement_whose_second_payout_cannot_be_queued_posts_nothing_and_leaves_it_authorized() {
    List<String> posted = Collections.synchronizedList(new ArrayList<>());
    WebhookDispatcher afterItsCommit =
        new WebhookDispatcher(
            deliveries,
            (url, payload) -> {
              posted.add(url);
              return new MerchantResponse.Answered(200, "");
            },
            new AutraDoubles.Audit(),
            AutraDoubles.clockAt(NOW),
            new OneIdOnly(),
            new RetryPlan(List.of(Duration.ZERO)),
            new DeliveryShaping(new InMemoryScenarioRules()),
            List.of(new AutraCallbackAcknowledgement()),
            List.of(),
            afterCommit);
    SettlePixPayment settlement =
        new SettlePixPayment(
            pixPayments,
            new AutraCallbacks(
                pixPayments,
                new AutraDoubles.Merchants()
                    .with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of())),
                new AutraDoubles.Accounts()
                    .callingBack(RUN, ACCOUNT, TARGET)
                    .callingBack(RUN, ACCOUNT, TARGET + "/second"),
                afterItsCommit,
                new AutraDoubles.CountingIds()),
            unitOfWork);
    stored(PIX_ID, AutraStatus.AUTHORIZED);

    assertThatThrownBy(() -> settlement.perform(settle(PIX_ID), NOW))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(posted).isEmpty();
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(queued()).isEmpty();
  }

  private AutraCallbacks callbacksMintingOneJobIdOnly() {
    WebhookDispatcher dispatcher =
        new WebhookDispatcher(
            deliveries,
            (url, payload) -> new MerchantResponse.Answered(200, ""),
            new AutraDoubles.Audit(),
            AutraDoubles.clockAt(NOW),
            new OneIdOnly(),
            new RetryPlan(List.of(Duration.ZERO)),
            new DeliveryShaping(new InMemoryScenarioRules()),
            List.of(new AutraCallbackAcknowledgement()),
            List.of(),
            OutsideAnyUnitOfWork.RUNS_AT_ONCE);
    return new AutraCallbacks(
        pixPayments,
        new AutraDoubles.Merchants().with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of())),
        new AutraDoubles.Accounts().callingBack(RUN, ACCOUNT, TARGET),
        dispatcher,
        new AutraDoubles.CountingIds());
  }

  private List<WebhookDelivery> queued() {
    return deliveries.of(AutraApi.AUTRA, RUN);
  }

  private static FollowUpRequest settle(String pixId) {
    return FollowUpRequest.of(RUN, pixId, SettlePixPayment.SETTLE, ObjectOrigin.CONTROL_API);
  }

  private static HostedCompletionRequest paid(String pixId) {
    return new HostedCompletionRequest(
        RUN, pixId, PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API);
  }

  private AutraPixPayment stored(String pixId, String nativeStatus) {
    AutraPixPayment pix =
        new AutraPixPayment(
            RUN,
            pixId,
            ACCOUNT,
            DOCUMENT,
            "pedido-" + pixId,
            "Pedido #1234",
            Amount.of("99.90"),
            "BRL",
            nativeStatus,
            CreatePixCheckout.theQrCodeOf(pixId),
            "A13CA0",
            "1A0B5369",
            PAID_AT.plus(CreatePixCheckout.THE_QR_LIVES_FOR),
            ObjectOrigin.PROVIDER_API,
            PAID_AT,
            PAID_AT);
    assertThat(pixPayments.insertIfAbsent(pix)).isTrue();
    return pix;
  }

  private static final class RemovedByAnotherRequestBeforeTheMove implements AutraPixPayments {

    private final AutraPixPayments stored;
    private final Runnable removal;

    RemovedByAnotherRequestBeforeTheMove(AutraPixPayments stored, Runnable removal) {
      this.stored = stored;
      this.removal = removal;
    }

    @Override
    public boolean insertIfAbsent(AutraPixPayment payment) {
      return stored.insertIfAbsent(payment);
    }

    @Override
    public Optional<AutraPixPayment> findById(TestRunId testRunId, String pixId) {
      return stored.findById(testRunId, pixId);
    }

    @Override
    public List<AutraPixPayment> allById(TestRunId testRunId, List<String> pixIds) {
      return stored.allById(testRunId, pixIds);
    }

    @Override
    public boolean moveFromPendingTo(AutraPixPayment payment, String nativeStatus, Instant at) {
      removal.run();
      return stored.moveFromPendingTo(payment, nativeStatus, at);
    }

    @Override
    public boolean moveFromAuthorizedToSettled(AutraPixPayment payment, Instant at) {
      removal.run();
      return stored.moveFromAuthorizedToSettled(payment, at);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pixId) {
      return stored.placeOf(testRunId, pixId);
    }

    @Override
    public List<ArrivalOf<AutraPixPayment>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      return stored.newestFirst(testRunId, after, limit);
    }

    @Override
    public int clear(TestRunId testRunId) {
      return stored.clear(testRunId);
    }

    @Override
    public int clearAll() {
      return stored.clearAll();
    }
  }

  private static final class OneIdOnly implements IdGenerator {

    @Override
    public String hex(int length) {
      return "a".repeat(length);
    }

    @Override
    public String digits(int length) {
      return "1".repeat(length);
    }
  }
}
