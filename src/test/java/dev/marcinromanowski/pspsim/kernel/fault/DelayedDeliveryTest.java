package dev.marcinromanowski.pspsim.kernel.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DelayedDeliveryTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final ProviderId OTHER = ProviderId.of("azericard");
  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("e2e-run-456");

  private final ManualScheduler scheduler = new ManualScheduler();
  private final ScheduledDeliveries deliveries = new ScheduledDeliveries(scheduler);

  private final List<String> delivered = new ArrayList<>();
  private final List<DeliveryAbandonment> abandoned = new ArrayList<>();

  @Test
  void a_held_response_is_delivered_when_its_delay_elapses_and_is_no_longer_held() {
    hold(RUN, "settlement", Duration.ofSeconds(5));

    assertThat(deliveries.stillHeld()).isEqualTo(1);
    assertThat(scheduler.delayOf("settlement")).isEqualTo(Duration.ofSeconds(5));

    scheduler.fire("settlement");

    assertThat(delivered).containsExactly("settlement");
    assertThat(abandoned).isEmpty();
    assertThat(deliveries.stillHeld()).isZero();
  }

  @Test
  void resetting_a_test_run_abandons_the_responses_it_was_holding_rather_than_delivering_them() {
    hold(RUN, "settlement", Duration.ofSeconds(5));
    hold(RUN, "refund", Duration.ofSeconds(5));
    hold(ANOTHER_RUN, "elsewhere", Duration.ofSeconds(5));

    assertThat(deliveries.cancel(GROW, RUN)).isEqualTo(2);

    assertThat(delivered).isEmpty();
    assertThat(abandoned)
        .containsExactly(DeliveryAbandonment.TEST_RUN_RESET, DeliveryAbandonment.TEST_RUN_RESET);
    assertThat(scheduler.cancelled()).containsExactlyInAnyOrder("settlement", "refund");
    assertThat(deliveries.stillHeld()).isEqualTo(1);

    scheduler.fire("elsewhere");
    assertThat(delivered).containsExactly("elsewhere");
  }

  @Test
  void a_reset_in_a_unit_abandons_its_held_responses_only_once_the_unit_commits() {
    List<Runnable> onceCommitted = new ArrayList<>();
    ScheduledDeliveries committing = new ScheduledDeliveries(scheduler, onceCommitted::add);
    scheduler.naming("settlement");
    committing.hold(
        GROW, RUN, Duration.ofSeconds(5), () -> delivered.add("settlement"), abandoned::add);
    scheduler.naming("elsewhere");
    committing.hold(
        GROW, ANOTHER_RUN, Duration.ofSeconds(5), () -> delivered.add("elsewhere"), abandoned::add);

    int counted = committing.cancel(GROW, RUN);

    assertThat(counted).isEqualTo(1);
    assertThat(abandoned).isEmpty();
    assertThat(committing.stillHeld()).isEqualTo(2);
    onceCommitted.forEach(Runnable::run);
    assertThat(abandoned).containsExactly(DeliveryAbandonment.TEST_RUN_RESET);
    assertThat(committing.stillHeld()).isEqualTo(1);
  }

  @Test
  void a_cancel_answers_how_many_responses_of_the_run_were_held_whether_or_not_it_waits() {
    hold(RUN, "settlement", Duration.ofSeconds(5));
    hold(RUN, "refund", Duration.ofSeconds(5));
    List<Runnable> onceCommitted = new ArrayList<>();
    ScheduledDeliveries committing = new ScheduledDeliveries(scheduler, onceCommitted::add);
    committing.hold(GROW, RUN, Duration.ofSeconds(5), () -> {}, abandoned::add);
    committing.hold(GROW, RUN, Duration.ofSeconds(5), () -> {}, abandoned::add);

    assertThat(deliveries.cancel(GROW, RUN)).isEqualTo(2);
    assertThat(committing.cancel(GROW, RUN)).isEqualTo(2);
    assertThat(committing.cancelAll(GROW)).isEqualTo(2);
    assertThat(onceCommitted).hasSize(2);
  }

  @Test
  void an_abandoned_response_is_never_also_delivered_when_its_timer_fires_afterwards() {
    hold(RUN, "settlement", Duration.ofSeconds(5));

    assertThat(deliveries.cancel(GROW, RUN)).isEqualTo(1);
    scheduler.fire("settlement");

    assertThat(delivered).isEmpty();
    assertThat(abandoned).containsExactly(DeliveryAbandonment.TEST_RUN_RESET);
  }

  @Test
  void a_delivered_response_is_never_afterwards_abandoned_by_a_reset() {
    hold(RUN, "settlement", Duration.ofSeconds(5));

    scheduler.fire("settlement");

    assertThat(deliveries.cancel(GROW, RUN)).isZero();
    assertThat(delivered).containsExactly("settlement");
    assertThat(abandoned).isEmpty();
  }

  @Test
  void resetting_every_test_run_of_one_provider_leaves_another_providers_responses_held() {
    hold(GROW, RUN, "settlement", Duration.ofSeconds(5));
    hold(GROW, ANOTHER_RUN, "elsewhere", Duration.ofSeconds(5));
    hold(OTHER, RUN, "another-provider", Duration.ofSeconds(5));

    assertThat(deliveries.cancelAll(GROW)).isEqualTo(2);

    assertThat(abandoned)
        .containsExactly(DeliveryAbandonment.TEST_RUN_RESET, DeliveryAbandonment.TEST_RUN_RESET);
    assertThat(deliveries.stillHeld()).isEqualTo(1);
    scheduler.fire("another-provider");
    assertThat(delivered).containsExactly("another-provider");
  }

  @Test
  void resetting_a_test_run_of_one_provider_leaves_the_same_test_run_of_another_alone() {
    hold(OTHER, RUN, "another-provider", Duration.ofSeconds(5));

    assertThat(deliveries.cancel(GROW, RUN)).isZero();
    assertThat(abandoned).isEmpty();
    assertThat(deliveries.stillHeld()).isEqualTo(1);
  }

  @Test
  void shutting_the_simulator_down_answers_every_held_response_instead_of_hanging_it() {
    hold(RUN, "settlement", Duration.ofSeconds(30));
    hold(ANOTHER_RUN, "elsewhere", Duration.ofSeconds(30));

    deliveries.close();

    assertThat(delivered).isEmpty();
    assertThat(abandoned)
        .containsExactly(
            DeliveryAbandonment.SIMULATOR_STOPPING, DeliveryAbandonment.SIMULATOR_STOPPING);
    assertThat(deliveries.stillHeld()).isZero();
  }

  @Test
  void a_response_the_container_settled_first_is_neither_delivered_nor_abandoned() {
    HeldDelivery delivery = hold(RUN, "settlement", Duration.ofSeconds(5));

    assertThat(delivery.settle()).isTrue();
    assertThat(delivery.settle()).isFalse();

    assertThat(deliveries.cancel(GROW, RUN)).isZero();
    scheduler.fire("settlement");

    assertThat(delivered).isEmpty();
    assertThat(abandoned).isEmpty();
  }

  @Test
  void a_ceiling_derives_the_deadline_the_container_must_wait_before_it_gives_up() {
    FaultDelayCeiling ceiling = new FaultDelayCeiling(Duration.ofSeconds(30));

    assertThat(ceiling.deliveryDeadline()).isEqualTo(Duration.ofSeconds(40));
    assertThat(ceiling.deliveryDeadline()).isGreaterThan(ceiling.longest());
  }

  @Test
  void a_ceiling_builds_the_only_delay_a_rule_may_hold_and_refuses_a_longer_one() {
    FaultDelayCeiling ceiling = new FaultDelayCeiling(Duration.ofSeconds(30));
    Duration oneSecondPastTheCeiling = Duration.ofSeconds(31);

    assertThat(ceiling.holdingFor(Duration.ofSeconds(30)))
        .isEqualTo(new TransportFault.DelayedResponse(Duration.ofSeconds(30)));
    assertThatThrownBy(() -> ceiling.holdingFor(oneSecondPastTheCeiling))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.fault.longest-delay");
    assertThatThrownBy(() -> ceiling.holdingFor(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action.delayMs must be at least one millisecond");
  }

  @Test
  void a_ceiling_that_can_perform_no_delay_at_all_is_refused_where_it_is_built() {
    Duration negative = Duration.ofSeconds(-1);
    Duration longerThanTheTypeAllows = Duration.ofMinutes(6);

    assertThatThrownBy(() -> new FaultDelayCeiling(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.fault.longest-delay must be positive");
    assertThatThrownBy(() -> new FaultDelayCeiling(negative))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.fault.longest-delay must be positive");
    assertThatThrownBy(() -> new FaultDelayCeiling(longerThanTheTypeAllows))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed PT5M");
    assertThat(new FaultDelayCeiling(TransportFault.LONGEST_DELAY).longest())
        .isEqualTo(Duration.ofMinutes(5));
  }

  private HeldDelivery hold(TestRunId testRunId, String name, Duration delay) {
    return hold(GROW, testRunId, name, delay);
  }

  private HeldDelivery hold(ProviderId provider, TestRunId testRunId, String name, Duration delay) {
    scheduler.naming(name);
    return deliveries.hold(provider, testRunId, delay, () -> delivered.add(name), abandoned::add);
  }

  private static final class ManualScheduler implements DelayScheduler {

    private final Map<Runnable, Duration> due = new LinkedHashMap<>();
    private final List<Runnable> pending = new ArrayList<>();
    private final List<Runnable> cancelledTasks = new ArrayList<>();
    private final Map<String, Runnable> named = new LinkedHashMap<>();

    private String naming = "";

    @Override
    public ScheduledDelivery after(Duration delay, Runnable delivery) {
      due.put(delivery, delay);
      pending.add(delivery);
      named.put(naming, delivery);
      return () -> cancelledTasks.add(delivery);
    }

    void naming(String name) {
      this.naming = name;
    }

    Duration delayOf(String name) {
      return due.get(named.get(name));
    }

    void fire(String name) {
      Runnable task = named.get(name);
      assertThat(pending).contains(task);
      pending.remove(task);
      task.run();
    }

    List<String> cancelled() {
      List<String> names = new ArrayList<>();
      named.forEach(
          (name, task) -> {
            if (cancelledTasks.contains(task)) {
              names.add(name);
            }
          });
      return names;
    }
  }
}
